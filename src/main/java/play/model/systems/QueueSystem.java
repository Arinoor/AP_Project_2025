package play.model.systems;

import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;
import play.model.constants.GameBalance;
import play.model.physics.Kinematics;

import java.util.*;

/**
 * Handles arrivals into systems, coins, buffering, dispatch, and:
 *  - INFINITE mid-wire collision: reverse at the exact collision position.
 *  - Per-frame jerk application (accel += jerk * dt).
 *  - SECURE adaptive slowdown: if dest system has queued items, clamp speed down.
 *  - VPN conversion: messaging packets entering a VPN become PROTECTED with hidden (random) emulation.
 *  - SPY systems: destroy secure packets, allow any packet to exit any spy system, don't affect protected packets.
 */
public class QueueSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;

        /** Per-device buffer of waiting seed entities. */
        private final Map<Entity, Deque<Entity>> deviceQueues = new HashMap<>();

        private final Random rng = new Random();

        // SECURE speed policy (re-uses SQUARE base speed; slow speed floored)
        private static final double SECURE_BASE_SPEED = GameBalance.SQUARE_BASE_SPEED;
        private static final double SECURE_SLOW_SPEED = Math.max(40.0, SECURE_BASE_SPEED * 0.35);

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = engine;
                this.entities = entities;
        }

        @Override
        public void update(double dt) {
                // 0) Decay "Disabled" timers on systems
                for (Entity e : entities) {
                        if (e.has(Disabled.class)) {
                                Disabled d = e.get(Disabled.class);
                                d.remaining -= dt;
                                if (d.remaining <= 0) {
                                        e.remove(Disabled.class);
                                }
                        }
                }

                revertPacketsFromDisabledVpns();

                // 0.20) SECURE adaptive slowdown: before physics/arrivals, ensure we gate speed
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.type != Seed.Type.SECURE) continue;
                        if (s.currentLink == null) continue; // only in-flight

                        // Determine the target system for this traversal (works for returning or forward)
                        Entity targetPort = s.returning ? s.currentLink.fromPort : s.currentLink.toPort;
                        if (targetPort == null || !targetPort.has(PortInfo.class)) continue;
                        Entity targetSystem = targetPort.get(PortInfo.class).parentSystem;

                        // Check if target system currently has a waiting queue (exclude null)
                        Deque<Entity> q = deviceQueues.get(targetSystem);
                        boolean targetBusy = (q != null && !q.isEmpty());

                        // Apply adaptive speed
                        if (targetBusy) {
                                if (s.speed > SECURE_SLOW_SPEED) s.speed = SECURE_SLOW_SPEED;
                                s.accel = 0.0;
                                s.jerk  = 0.0;
                        } else {
                                if (s.speed < SECURE_BASE_SPEED) s.speed = SECURE_BASE_SPEED;
                                s.accel = 0.0;
                                s.jerk  = 0.0;
                        }
                }

                // 0.25) Apply jerk each frame: accel += jerk * dt (for all seeds; jerk usually 0)
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue; // only while traversing

                        if (s.jerk != 0.0) {
                                s.accel += s.jerk * dt;
                                if (s.jerk < 0.0 && s.accel < 0.0) {
                                        s.accel = 0.0; // don't flip into braking unless explicitly designed
                                }
                        }
                }

                // 0.5) INFINITE mid-wire collision handling: reverse AT THE COLLISION POSITION
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.type != Seed.Type.INFINITE) continue;

                        if (s.currentLink == null || s.returning) continue;

                        if (s.justCollided) {
                                double oldP = clamp01(s.progress);
                                s.returning = true;
                                s.progress = 1.0 - oldP;

                                s.impactEnergy = Math.max(s.impactEnergy, 0.6);
                                s.justCollided = false;
                        }
                }

                // 1) Collect arrivals this frame
                List<Entity> arrivals = new ArrayList<>();
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;
                        if (s.progress < 1.0) continue;
                        arrivals.add(e);
                }

                // 2) Process arrivals (speed trap, became-disabled bounce, normal handling)
                for (Entity seedE : arrivals) {
                        Seed s = seedE.get(Seed.class);
                        Link link = s.currentLink;

                        // Determine actual destination port for this traversal
                        Entity inPort = s.returning ? link.fromPort : link.toPort;
                        PortInfo pTo = inPort.get(PortInfo.class);
                        Entity system = pTo.parentSystem;

                        // Destination port position
                        Transform tIn = inPort.get(Transform.class);
                        Transform st  = seedE.get(Transform.class);

                        // Speed trap: disable & bounce back (only on first arrival)
                        if (!s.returning && s.speed > GameBalance.ENTRY_SPEED_LIMIT) {
                                if (system.has(Disabled.class)) {
                                        system.get(Disabled.class).remaining = GameBalance.SYSTEM_DISABLE_SECONDS;
                                } else {
                                        system.add(new Disabled(GameBalance.SYSTEM_DISABLE_SECONDS));
                                }
                                s.returning = true;
                                s.progress  = 0.0;
                                continue;
                        }

                        // Destination may have become disabled while en route -> bounce back
                        if (!s.returning && system.has(Disabled.class)) {
                                s.returning = true;
                                s.progress  = 0.0;
                                continue;
                        }

                        // Normal arrival handling
                        st.x = tIn.x; st.y = tIn.y;
                        s.currentLink = null;
                        s.progress = 0.0;
                        s.lateral  = 0.0;

                        boolean finishedReturn = s.returning;
                        s.returning = false;

                        // === Award coins on any successful arrival (including sinks) ===
                        // square:2, triangle:3, infinite:1, secure:3, protected:5
                        if (!finishedReturn) {
                                int reward;
                                switch (s.type) {
                                        case SQUARE:   reward = 2; break;
                                        case TRIANGLE: reward = 3; break;
                                        case INFINITE: reward = 1; break;
                                        case SECURE:   reward = 3; break;
                                        case PROTECTED:reward = 5; break;
                                        default:       reward = 0; break;
                                }
                                engine.incrementCoins(reward);
                        }

                        // === Reference systems consume ===
                        if (system.has(Reference.class)) {
                                engine.incrementReachedReference();
                                engine.notifySeedDelivered(s);
                                entities.remove(seedE);
                                continue;
                        }

                        // === SPY system handling ===
                        if (system.has(Spy.class)) {
                                handleSpySystemArrival(system, seedE, s);
                                // If the packet was destroyed, continue to next arrival
                                if (!entities.contains(seedE)) continue;
                        }

                        // === VPN conversion (on entry to system) ===
                        if (system.has(Vpn.class)) {
                                // Only convert messaging packets (SQUARE/TRIANGLE/INFINITE). SECURE and PROTECTED passthrough.
                                if (s.type == Seed.Type.SQUARE || s.type == Seed.Type.TRIANGLE || s.type == Seed.Type.INFINITE) {
                                        Seed.Type base = s.type;
                                        Seed.Type emu  = pickRandomMessengerType();

                                        Seed newS = new Seed(Seed.Type.PROTECTED);
                                        newS.protectedBaseType = base;
                                        newS.emulateType = emu;
                                        // Adopt base capacity (triangle=4, else 3)
                                        newS.capacity = (base == Seed.Type.TRIANGLE) ? 4 : 3;
                                        // Reset motion; next hop will assign kinematics
                                        newS.speed = 0.0; newS.accel = 0.0; newS.jerk = 0.0;
                                        newS.noise = s.noise; // carry noise forward
                                        newS.impactEnergy = Math.max(s.impactEnergy, 0.25); // small flash on conversion

                                        newS.vpnConverter = system;

                                        // Replace component on the same entity
                                        seedE.remove(Seed.class);
                                        seedE.add(newS);
                                        s = newS;
                                }
                        }

                        // Enqueue into the device buffer
                        Deque<Entity> buf = deviceQueues.computeIfAbsent(system, k -> new ArrayDeque<>());
                        if (buf.size() >= GameBalance.DEVICE_CAPACITY) {
                                entities.remove(seedE);
                                engine.incrementLost();
                                continue;
                        }
                        buf.addLast(seedE);
                }

                // 3) Try to flush each device's queue to an available OUT link
                for (Map.Entry<Entity, Deque<Entity>> entry : deviceQueues.entrySet()) {
                        Entity system = entry.getKey();
                        Deque<Entity> buf = entry.getValue();
                        if (buf.isEmpty()) continue;

                        // Build lists of free OUT links from this system, grouped by shape
                        List<Entity> freeSquare = new ArrayList<>();
                        List<Entity> freeTriangle = new ArrayList<>();
                        for (Entity e : entities) {
                                if (!e.has(Link.class)) continue;
                                Link l = e.get(Link.class);
                                if (l.fromPort == null) continue;
                                if (!l.fromPort.has(PortInfo.class)) continue;
                                PortInfo pFrom = l.fromPort.get(PortInfo.class);
                                if (pFrom.parentSystem != system) continue;
                                if (!isLinkFree(l)) continue;

                                if (l.toPort == null || !l.toPort.has(PortInfo.class)) continue;
                                PortInfo destPi = l.toPort.get(PortInfo.class);
                                Entity destSys = destPi.parentSystem;
                                if (destSys != null && destSys.has(Disabled.class)) continue;

                                if (pFrom.shape == PortInfo.Shape.SQUARE) freeSquare.add(e);
                                else freeTriangle.add(e);
                        }

                        // For packets that have been in spy systems, add all spy system links
                        List<Entity> spyLinks = new ArrayList<>();
                        Entity seedE = buf.peekFirst();
                        if (seedE != null && seedE.has(Seed.class)) {
                                Seed s = seedE.get(Seed.class);
                                if (s.hasBeenInSpySystem) {
                                        for (Entity e : entities) {
                                                if (!e.has(Link.class)) continue;
                                                Link l = e.get(Link.class);
                                                if (l.fromPort == null) continue;
                                                if (!l.fromPort.has(PortInfo.class)) continue;
                                                PortInfo pFrom = l.fromPort.get(PortInfo.class);
                                                if (!pFrom.parentSystem.has(Spy.class)) continue;
                                                if (!isLinkFree(l)) continue;

                                                if (l.toPort == null || !l.toPort.has(PortInfo.class)) continue;
                                                PortInfo destPi = l.toPort.get(PortInfo.class);
                                                Entity destSys = destPi.parentSystem;
                                                if (destSys != null && destSys.has(Disabled.class)) continue;

                                                if (pFrom.shape == PortInfo.Shape.SQUARE) {
                                                        if (!freeSquare.contains(e)) freeSquare.add(e);
                                                } else {
                                                        if (!freeTriangle.contains(e)) freeTriangle.add(e);
                                                }
                                        }
                                }
                        }

                        // While we can ship something out, do it
                        boolean progressed = true;
                        while (progressed && !buf.isEmpty()) {
                                progressed = false;

                                seedE = buf.peekFirst();
                                if (seedE == null) break;
                                Seed s = seedE.get(Seed.class);

                                // Preferred links:
                                // - SQUARE / INFINITE: square links
                                // - TRIANGLE: triangle links
                                // - SECURE: any available (no compatibility concept)
                                // - PROTECTED: choose based on emulateType (hidden)
                                Entity chosenLinkE = null;
                                if (s.type == Seed.Type.SQUARE || s.type == Seed.Type.INFINITE) {
                                        if (!freeSquare.isEmpty()) chosenLinkE = freeSquare.remove(0);
                                } else if (s.type == Seed.Type.TRIANGLE) {
                                        if (!freeTriangle.isEmpty()) chosenLinkE = freeTriangle.remove(0);
                                } else if (s.type == Seed.Type.SECURE) {
                                        int total = freeSquare.size() + freeTriangle.size();
                                        if (total > 0) {
                                                int idx = rng.nextInt(total);
                                                chosenLinkE = (idx < freeSquare.size())
                                                        ? freeSquare.remove(idx)
                                                        : freeTriangle.remove(idx - freeSquare.size());
                                        }
                                } else if (s.type == Seed.Type.PROTECTED) {
                                        Seed.Type emu = (s.emulateType != null) ? s.emulateType : Seed.Type.SQUARE;
                                        if (emu == Seed.Type.TRIANGLE) {
                                                if (!freeTriangle.isEmpty()) chosenLinkE = freeTriangle.remove(0);
                                        } else { // SQUARE or INFINITE emulation => use square link
                                                if (!freeSquare.isEmpty()) chosenLinkE = freeSquare.remove(0);
                                        }
                                        // if none, fall through to random below
                                }

                                if (chosenLinkE == null) {
                                        int total = freeSquare.size() + freeTriangle.size();
                                        if (total > 0) {
                                                int idx = rng.nextInt(total);
                                                chosenLinkE = (idx < freeSquare.size())
                                                        ? freeSquare.remove(idx)
                                                        : freeTriangle.remove(idx - freeSquare.size());
                                        }
                                }

                                if (chosenLinkE == null) break; // congested

                                // We can dispatch this seed
                                buf.removeFirst();

                                Link l = chosenLinkE.get(Link.class);
                                PortInfo.Shape outShape = l.fromPort.get(PortInfo.class).shape;

                                // per-hop kinematics
                                Kinematics.applyForHop(s, outShape);

                                // place at OUT port and start hop
                                Transform tFrom = l.fromPort.get(Transform.class);
                                Transform st = seedE.get(Transform.class);
                                st.x = tFrom.x; st.y = tFrom.y;

                                s.currentLink = l;
                                s.progress = 0.0;
                                s.returning = false;

                                progressed = true;
                        }
                }
        }

        /**
         * Handle packets arriving at spy systems
         * - Secure packets are destroyed
         * - Protected packets are unaffected
         * - Other packets are marked as having been in a spy system
         */
        private void handleSpySystemArrival(Entity system, Entity seedE, Seed s) {
                // Destroy secure packets
                if (s.type == Seed.Type.SECURE) {
                        entities.remove(seedE);
                        engine.incrementLost();
                        return;
                }

                // Protected packets are unaffected
                if (s.type == Seed.Type.PROTECTED) {
                        return;
                }

                // Mark other packets as having been in a spy system
                s.hasBeenInSpySystem = true;
        }

        private void revertPacketsFromDisabledVpns() {
                for (Entity e : entities) {
                        if (e.has(Seed.class)) {
                                Seed s = e.get(Seed.class);
                                // Check if this is a protected packet from a disabled VPN
                                if (s.type == Seed.Type.PROTECTED &&
                                        s.vpnConverter != null &&
                                        s.vpnConverter.has(Disabled.class)) {

                                        // Revert to original type
                                        s.type = s.protectedBaseType;
                                        s.protectedBaseType = null;
                                        s.emulateType = null;
                                        s.vpnConverter = null;

                                        // Reset to appropriate capacity
                                        s.capacity = (s.type == Seed.Type.TRIANGLE) ? 4 : 3;
                                }
                        }
                }
        }

        private Seed.Type pickRandomMessengerType() {
                int r = rng.nextInt(3);
                return (r == 0) ? Seed.Type.SQUARE : (r == 1) ? Seed.Type.TRIANGLE : Seed.Type.INFINITE;
        }

        private boolean isLinkFree(Link link) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false; // one seed per wire
                }
                return true;
        }

        private static double clamp01(double v) {
                if (v < 0) return 0;
                if (v > 1) return 1;
                return v;
        }
}