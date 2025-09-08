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
                                // maintain no accel/jerk for SECURE
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

                        // Must be on a wire and not already returning
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

                        // === Coin rewards per type ===
                        // square:2, triangle:3, infinite:1, secure:3
                        int reward;
                        switch (s.type) {
                                case SQUARE:   reward = 2; break;
                                case TRIANGLE: reward = 3; break;
                                case INFINITE: reward = 1; break;
                                case SECURE:   reward = 3; break;
                                default:       reward = 0; break;
                        }
                        if (!finishedReturn) {
                                engine.incrementCoins(reward);
                        }

                        // Reference systems consume
                        if (system.has(Reference.class)) {
                                engine.incrementReachedReference();
                                engine.notifySeedDelivered(s);
                                entities.remove(seedE);
                                continue;
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

                        // While we can ship something out, do it
                        boolean progressed = true;
                        while (progressed && !buf.isEmpty()) {
                                progressed = false;

                                Entity seedE = buf.peekFirst();
                                if (seedE == null) break;
                                Seed s = seedE.get(Seed.class);

                                // Preferred links:
                                // - SQUARE / INFINITE: square links
                                // - TRIANGLE: triangle links
                                // - SECURE: any available (no compatibility concept)
                                Entity chosenLinkE = null;
                                if ((s.type == Seed.Type.SQUARE || s.type == Seed.Type.INFINITE) && !freeSquare.isEmpty()) {
                                        chosenLinkE = freeSquare.remove(0);
                                } else if (s.type == Seed.Type.TRIANGLE && !freeTriangle.isEmpty()) {
                                        chosenLinkE = freeTriangle.remove(0);
                                } else if (s.type == Seed.Type.SECURE) {
                                        int total = freeSquare.size() + freeTriangle.size();
                                        if (total > 0) {
                                                int idx = rng.nextInt(total);
                                                chosenLinkE = (idx < freeSquare.size())
                                                        ? freeSquare.remove(idx)
                                                        : freeTriangle.remove(idx - freeSquare.size());
                                        }
                                }

                                // If none selected yet, grab any free link
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

                                // per-hop kinematics (INFINITE/SECURE rules applied inside)
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
