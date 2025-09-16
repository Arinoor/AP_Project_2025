
package play.model.systems;

import play.model.core.Entity;
import play.model.components.Disabled;
import play.model.components.Link;
import play.model.components.PortInfo;
import play.model.components.Reference;
import play.model.components.Seed;
import play.model.components.Spy;
import play.model.components.Vpn;
import play.model.components.Saboteur;
import play.model.engine.GameEngine;
import play.model.constants.GameBalance;
import play.model.physics.Kinematics;
import play.model.components.Transform;

import java.util.*;

/**
 * QueueSystem
 *
 * Responsibilities:
 *  - Buffer seeds when they arrive at systems
 *  - Apply special per-system arrival effects (VPN, Spy, Saboteur)
 *  - Select and dispatch seeds to available outbound links using system rules
 *  - Handle INFINITE mid-wire collision reversal
 *  - Apply per-frame physics adjustments (jerk -> accel) and SECURE adaptive slowdown
 *  - Revert PROTECTED seeds when their source VPN becomes disabled
 *
 * Design notes:
 *  - Large update() is decomposed into small private methods: each has a focused responsibility.
 *  - Data structures are intentionally straightforward: Map<SystemEntity, Deque<SeedEntity>> deviceQueues.
 */
public final class QueueSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;
        private final Map<Entity, Deque<Entity>> deviceQueues = new HashMap<>();
        private final Random rng = new Random();

        // SECURE speed constants
        private static final double SECURE_BASE_SPEED = GameBalance.SQUARE_BASE_SPEED;
        private static final double SECURE_SLOW_SPEED = Math.max(40.0, SECURE_BASE_SPEED * 0.35);

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = Objects.requireNonNull(engine, "engine");
                this.entities = Objects.requireNonNull(entities, "entities");
        }

        @Override
        public void update(double dt) {
                processDisabledTimers(dt);
                runAntitrojanSweep();   // <-- NEW: scan and clean trojans nearby
                revertPacketsFromDisabledVpns();

                applySecureAdaptiveSlowdown();
                applyPerFrameJerk(dt);
                handleInfiniteWireCollisions();

                List<Entity> arrivals = collectArrivals();
                processArrivals(arrivals);

                flushAllDeviceQueues();
        }

        // ---------------------------
        // Top-level helper methods
        // ---------------------------

        private void processDisabledTimers(double dt) {
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Disabled.class)) continue;
                        Disabled d = e.get(Disabled.class);
                        d.remaining -= dt;
                        if (d.remaining <= 0) {
                                e.remove(Disabled.class);
                        }
                }
        }

        /**
         * For all SECURE seeds that are currently in-flight, if their destination system
         * has queued items, clamp speed to SECURE_SLOW_SPEED. Otherwise ensure base speed.
         */
        private void applySecureAdaptiveSlowdown() {
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.type != Seed.Type.SECURE) continue;
                        Link link = s.currentLink;
                        if (link == null) continue;

                        // determine destination system for this hop
                        Entity destPort = s.returning ? link.fromPort : link.toPort;
                        if (destPort == null || !destPort.has(PortInfo.class)) continue;
                        Entity destSystem = destPort.get(PortInfo.class).parentSystem;
                        if (destSystem == null) continue;

                        Deque<Entity> q = deviceQueues.get(destSystem);
                        boolean busy = q != null && !q.isEmpty();
                        if (busy) {
                                if (s.speed > SECURE_SLOW_SPEED) s.speed = SECURE_SLOW_SPEED;
                                s.accel = 0.0;
                                s.jerk = 0.0;
                        } else {
                                if (s.speed < SECURE_BASE_SPEED) s.speed = SECURE_BASE_SPEED;
                                s.accel = 0.0;
                                s.jerk = 0.0;
                        }
                }
        }


        private void applyPerFrameJerk(double dt) {
                if (dt <= 0) return;
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;
                        if (s.jerk == 0.0) continue;

                        s.accel += s.jerk * dt;
                        // safety: do not allow negative accel from jerk (preserve expected behavior)
                        if (s.jerk < 0.0 && s.accel < 0.0) {
                                s.accel = 0.0;
                        }
                }
        }

        /**
         * INFINITE seeds: when they had a collision flagged (justCollided), flip returning
         * status and adjust progress so reversal occurs exactly at collision point.
         */
        private void handleInfiniteWireCollisions() {
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.type != Seed.Type.INFINITE) continue;
                        if (s.currentLink == null || s.returning) continue;
                        if (!s.justCollided) continue;

                        double oldP = clamp01(s.progress);
                        s.returning = true;
                        s.progress = 1.0 - oldP;
                        s.impactEnergy = Math.max(s.impactEnergy, 0.6);
                        s.justCollided = false;
                }
        }

        /**
         * Collect entities that have completed their hop this frame (progress >= 1.0).
         */
        private List<Entity> collectArrivals() {
                List<Entity> arrivals = new ArrayList<>();
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;
                        if (s.progress >= 1.0) arrivals.add(e);
                }
                return arrivals;
        }

        /**
         * Process each arriving seed entity: bounce on disabled or speed-limit, award coins,
         * apply VPN conversion, spy behavior, saboteur arrival injection and finally enqueue.
         */
        private void processArrivals(List<Entity> arrivals) {
                for (Entity seedEntity : arrivals) {
                        // defensive: seed may have been removed by previous arrival handling
                        if (!entities.contains(seedEntity) || !seedEntity.has(Seed.class)) continue;
                        Seed s = seedEntity.get(Seed.class);
                        Link link = s.currentLink;
                        if (link == null) continue;

                        Entity inPort = s.returning ? link.fromPort : link.toPort;
                        if (inPort == null || !inPort.has(PortInfo.class)) {
                                // safety: if malformed port, remove seed to avoid stuck state
                                entities.remove(seedEntity);
                                engine.incrementLost();
                                continue;
                        }

                        Entity system = inPort.get(PortInfo.class).parentSystem;
                        Transform seedTransform = seedEntity.get(Transform.class);
                        Transform portTransform = inPort.get(Transform.class);

                        // Speed trap: if entry speed excessive, disable target and bounce
                        if (!s.returning && s.speed > GameBalance.ENTRY_SPEED_LIMIT) {
                                ensureSystemDisabled(system, GameBalance.SYSTEM_DISABLE_SECONDS);
                                bounceSeedBack(s);
                                continue;
                        }

                        // If target became disabled while in-flight, bounce back
                        if (!s.returning && system != null && system.has(Disabled.class)) {
                                bounceSeedBack(s);
                                continue;
                        }

                        // Normal arrival - place seed at port coordinates and clear link
                        if (seedTransform != null && portTransform != null) {
                                seedTransform.x = portTransform.x;
                                seedTransform.y = portTransform.y;
                        }
                        s.currentLink = null;
                        s.progress = 0.0;
                        s.lateral = 0.0;
                        boolean wasReturn = s.returning;
                        s.returning = false;

                        // award coins on successful arrival (only for forward arrivals)
                        if (!wasReturn) awardArrivalCoins(s);

                        // Reference system consumes packet
                        if (system != null && system.has(Reference.class)) {
                                engine.incrementReachedReference();
                                engine.notifySeedDelivered(s);
                                entities.remove(seedEntity);
                                continue;
                        }

                        // Spy system handling - can remove seed or mark it
                        if (system != null && system.has(Spy.class)) {
                                boolean destroyed = handleSpySystemArrival(system, seedEntity, s);
                                if (destroyed) continue;
                        }

                        // VPN conversion (on entry)
                        if (system != null && system.has(Vpn.class)) {
                                handleVpnConversion(system, seedEntity, s);
                                // seedEntity now has possibly new Seed component
                                if (!seedEntity.has(Seed.class)) continue;
                                s = seedEntity.get(Seed.class);
                        }

                        // Saboteur arrival effect: noise injection (only for non-PROTECTED)
                        if (system != null && system.has(Saboteur.class)) {
                                handleSaboteurArrival(system, s);
                        }

                        // Enqueue into device buffer (respect capacity)
                        enqueueToDevice(system, seedEntity);
                }
        }

        // ---------------------------
        // Arrival sub-helpers
        // ---------------------------

        private void ensureSystemDisabled(Entity system, double seconds) {
                if (system == null) return;
                if (system.has(Disabled.class)) {
                        system.get(Disabled.class).remaining = seconds;
                } else {
                        system.add(new Disabled(seconds));
                }
        }

        private void bounceSeedBack(Seed s) {
                s.returning = true;
                s.progress = 0.0;
        }

        private void awardArrivalCoins(Seed s) {
                int reward;
                switch (s.type) {
                        case SQUARE: reward = 2; break;
                        case TRIANGLE: reward = 3; break;
                        case INFINITE: reward = 1; break;
                        case SECURE: reward = 3; break;
                        case PROTECTED: reward = 5; break;
                        default: reward = 0; break;
                }
                engine.incrementCoins(reward);
        }

        /**
         * Spy system arrival semantics.
         *  - SECURE seeds are destroyed (removed).
         *  - PROTECTED seeds unaffected.
         *  - Others are marked as having been in a spy system for later dispatch rules.
         *
         * @return true if the seed entity was destroyed / removed.
         */
        private boolean handleSpySystemArrival(Entity system, Entity seedEntity, Seed s) {
                if (s.type == Seed.Type.SECURE) {
                        entities.remove(seedEntity);
                        engine.incrementLost();
                        return true;
                }
                if (s.type == Seed.Type.PROTECTED) {
                        return false;
                }
                s.hasBeenInSpySystem = true;
                return false;
        }

        /**
         * Convert eligible messenger seeds into PROTECTED seeds upon entering a VPN system.
         * Conversion preserves noise and sets vpnConverter reference so potential reversion can happen.
         *
         * This method replaces the Seed component on the same entity when conversion occurs.
         */
        private void handleVpnConversion(Entity vpnSystem, Entity seedEntity, Seed s) {
                if (s == null) return;

                if (s.type == Seed.Type.SECURE) {
                        Seed newSeed = new Seed(Seed.Type.SECURE_PROTECTED);
                        newSeed.speed = s.speed;
                        newSeed.arcPos = s.arcPos;
                        newSeed.progress = s.progress;
                        // Copy other relevant properties...

                        seedEntity.remove(Seed.class);
                        seedEntity.add(newSeed);
                        return;
                }

                if (s.type != Seed.Type.SQUARE && s.type != Seed.Type.TRIANGLE && s.type != Seed.Type.INFINITE) {
                        // SECURE and PROTECTED types pass through
                        return;
                }


                Seed.Type originalBase = s.type;
                Seed.Type emulate = pickRandomMessengerType();

                Seed newSeed = new Seed(Seed.Type.PROTECTED);
                newSeed.protectedBaseType = originalBase;
                newSeed.emulateType = emulate;
                newSeed.capacity = (originalBase == Seed.Type.TRIANGLE) ? 4 : 3;
                newSeed.speed = 0.0;
                newSeed.accel = 0.0;
                newSeed.jerk = 0.0;
                newSeed.noise = s.noise;
                newSeed.impactEnergy = Math.max(s.impactEnergy, 0.25);
                newSeed.vpnConverter = vpnSystem;

                seedEntity.remove(Seed.class);
                seedEntity.add(newSeed);
        }

        /**
         * Saboteur arrival behavior:
         *  - Does not affect PROTECTED seeds.
         *  - If seed has 0 noise, inject 1.0 noise unit.
         */
        private void handleSaboteurArrival(Entity saboteurSystem, Seed s) {
                if (s == null) return;
                // do not affect PROTECTED-family packets
                if (s.type == Seed.Type.PROTECTED || s.type == Seed.Type.SECURE_PROTECTED) return;

                // existing noise injection
                if (s.noise == 0.0) s.noise += 1.0;

                // trojan tagging with probability (do not tag SECURE types or PROTECTED already handled)
                // Use RNG field 'rng' already present in this class
                if (!s.trojan) { // only if not already trojan
                        if (rng.nextDouble() < GameBalance.SABOTEUR_TROJAN_PROB) {
                                s.trojan = true;
                        }
                }
        }

        private void enqueueToDevice(Entity system, Entity seedEntity) {
                if (system == null) {
                        // If no target system (malformed), drop the seed to avoid leaks
                        entities.remove(seedEntity);
                        engine.incrementLost();
                        return;
                }

                Deque<Entity> queue = deviceQueues.computeIfAbsent(system, k -> new ArrayDeque<>());
                if (queue.size() >= GameBalance.DEVICE_CAPACITY) {
                        // device full: drop packet
                        entities.remove(seedEntity);
                        engine.incrementLost();
                        return;
                }
                queue.addLast(seedEntity);
        }

        // ---------------------------
        // Dispatch / flush logic
        // ---------------------------

        private void flushAllDeviceQueues() {
                // iterate over a snapshot of keys to avoid CME if queues are modified
                for (Entity system : new ArrayList<>(deviceQueues.keySet())) {
                        Deque<Entity> queue = deviceQueues.get(system);
                        if (queue == null || queue.isEmpty()) continue;
                        flushSingleDeviceQueue(system, queue);
                }
        }

        private void flushSingleDeviceQueue(Entity system, Deque<Entity> queue) {
                List<Entity> freeSquareLinks = new ArrayList<>();
                List<Entity> freeTriangleLinks = new ArrayList<>();

                collectFreeOutboundLinks(system, freeSquareLinks, freeTriangleLinks);

                // Check if head seed has been in spy system and add spy links
                Entity head = queue.peekFirst();
                if (head != null && head.has(Seed.class) && head.get(Seed.class).hasBeenInSpySystem) {
                        collectFreeOutboundLinksFromSpySystems(freeSquareLinks, freeTriangleLinks);
                }

                boolean progress = true;
                while (progress && !queue.isEmpty()) {
                        progress = tryDispatchOneFromQueue(system, queue, freeSquareLinks, freeTriangleLinks);
                }
        }

        /**
         * Collects free outbound links from given system into provided lists partitioned by port shape.
         */
        private void collectFreeOutboundLinks(Entity system,
                                              List<Entity> freeSquare,
                                              List<Entity> freeTriangle) {
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (l.fromPort == null) continue;
                        PortInfo pFrom = l.fromPort.get(PortInfo.class);
                        if (pFrom == null || pFrom.parentSystem != system) continue;
                        if (!isLinkFree(l)) continue;
                        if (l.toPort == null || !l.toPort.has(PortInfo.class)) continue;
                        PortInfo destPi = l.toPort.get(PortInfo.class);
                        if (destPi != null && destPi.parentSystem != null && destPi.parentSystem.has(Disabled.class)) continue;

                        if (pFrom.shape == PortInfo.Shape.SQUARE) freeSquare.add(e);
                        else freeTriangle.add(e);
                }
        }

        private void collectFreeOutboundLinksFromSpySystems(List<Entity> freeSquare, List<Entity> freeTriangle) {
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (l.fromPort == null) continue;
                        if (!l.fromPort.has(PortInfo.class)) continue;
                        PortInfo pFrom = l.fromPort.get(PortInfo.class);
                        if (pFrom.parentSystem == null || !pFrom.parentSystem.has(Spy.class)) continue;
                        if (!isLinkFree(l)) continue;
                        if (l.toPort == null || !l.toPort.has(PortInfo.class)) continue;
                        PortInfo destPi = l.toPort.get(PortInfo.class);
                        Entity destSys = destPi.parentSystem;
                        if (destSys != null && destSys.has(Disabled.class)) continue;

                        // Avoid duplicates
                        if (pFrom.shape == PortInfo.Shape.SQUARE) {
                                if (!freeSquare.contains(e)) freeSquare.add(e);
                        } else {
                                if (!freeTriangle.contains(e)) freeTriangle.add(e);
                        }
                }
        }

        /**
         * Attempt to dispatch the head of the queue according to the system rules.
         * Returns true if a seed was dispatched (progressed).
         */
        private boolean tryDispatchOneFromQueue(Entity system,
                                                Deque<Entity> queue,
                                                List<Entity> freeSquareLinks,
                                                List<Entity> freeTriangleLinks) {
                Entity seedEntity = queue.peekFirst();
                if (seedEntity == null || !seedEntity.has(Seed.class)) {
                        queue.pollFirst();
                        return false;
                }
                Seed s = seedEntity.get(Seed.class);

                // Determine chosen link according to seed type and Saboteur rule
                Entity chosenLinkEntity = selectOutboundLink(system, s, freeSquareLinks, freeTriangleLinks);

                if (chosenLinkEntity == null) {
                        // no available link to dispatch now
                        return false;
                }

                // perform dispatch
                queue.removeFirst();
                Link chosenLink = chosenLinkEntity.get(Link.class);

                // apply per-hop kinematics using outgoing port shape
                PortInfo outPi = chosenLink.fromPort.get(PortInfo.class);
                Kinematics.applyForHop(s, outPi.shape);

                // position seed at the fromPort transform
                Transform fromTransform = chosenLink.fromPort.get(Transform.class);
                Transform st = seedEntity.get(Transform.class);
                if (fromTransform != null && st != null) {
                        st.x = fromTransform.x;
                        st.y = fromTransform.y;
                }

                s.currentLink = chosenLink;
                s.progress = 0.0;
                s.returning = false;

                return true;
        }

        /**
         * Selection rules:
         *  - Normal preference:
         *      SQUARE/INFINITE => prefer square links
         *      TRIANGLE => prefer triangle links
         *      SECURE => random among available
         *      PROTECTED => use emulateType to choose, otherwise random
         *  - Saboteur system modification:
         *      If the source system has Saboteur component, non-PROTECTED seeds should prefer INCOMPATIBLE links first.
         *      Protected seeds are NOT affected by Saboteur selection (use normal rules).
         *
         * This method mutates the freeSquare/freeTriangle lists when it consumes a link.
         */
        private Entity selectOutboundLink(Entity sourceSystem,
                                          Seed s,
                                          List<Entity> freeSquare,
                                          List<Entity> freeTriangle) {
                boolean sourceIsSaboteur = sourceSystem != null && sourceSystem.has(Saboteur.class);

                // If source is saboteur and seed is non-protected, attempt incompatible-first selection
                if (sourceIsSaboteur && s.type != Seed.Type.PROTECTED) {
                        Entity chosen = chooseIncompatibleFirst(s, freeSquare, freeTriangle);
                        if (chosen != null) return chosen;
                        // otherwise fall-through to normal selection
                }

                // Normal selection rules:
                switch (s.type) {
                        case SQUARE:
                        case INFINITE:
                                if (!freeSquare.isEmpty()) return freeSquare.remove(0);
                                break;
                        case TRIANGLE:
                                if (!freeTriangle.isEmpty()) return freeTriangle.remove(0);
                                break;
                        case SECURE:
                                return removeRandomFromLists(freeSquare, freeTriangle);
                        case PROTECTED:
                                // PROTECTED emulateType determines preference
                                Seed.Type emu = (s.emulateType != null) ? s.emulateType : Seed.Type.SQUARE;
                                if (emu == Seed.Type.TRIANGLE) {
                                        if (!freeTriangle.isEmpty()) return freeTriangle.remove(0);
                                } else {
                                        if (!freeSquare.isEmpty()) return freeSquare.remove(0);
                                }
                                break;
                        case SECURE_PROTECTED:
                                if (!freeSquare.isEmpty() || !freeTriangle.isEmpty()) {
                                        return removeRandomFromLists(freeSquare, freeTriangle);
                                }
                        default:
                                break;
                }

                // fallback: pick any available link at random
                return removeRandomFromLists(freeSquare, freeTriangle);
        }

        /**
         * For SABOTEUR systems: pick an incompatible link first if present.
         * - SQUARE/INFINITE seeds prefer TRIANGLE links first (incompatible), else square.
         * - TRIANGLE seeds prefer SQUARE links first, else triangle.
         */
        private Entity chooseIncompatibleFirst(Seed s, List<Entity> freeSquare, List<Entity> freeTriangle) {
                if (s.type == Seed.Type.SQUARE || s.type == Seed.Type.INFINITE) {
                        if (!freeTriangle.isEmpty()) return freeTriangle.remove(0);
                        if (!freeSquare.isEmpty()) return freeSquare.remove(0);
                        return null;
                } else if (s.type == Seed.Type.TRIANGLE) {
                        if (!freeSquare.isEmpty()) return freeSquare.remove(0);
                        if (!freeTriangle.isEmpty()) return freeTriangle.remove(0);
                        return null;
                }
                // SECURE and PROTECTED handled elsewhere
                return null;
        }

        /**
         * Scan for active Antitrojan systems. If any trojan-tagged seed is within
         * ANTITROJAN_RADIUS of the system, clear the seed.trojan flag and disable
         * the system for ANTITROJAN_DISABLE_SECONDS. One successful cleanup per
         * system per sweep (avoid multi-clean per frame).
         */
        private void runAntitrojanSweep() {
                // iterate systems (use snapshot to be safe)
                for (Entity system : new ArrayList<>(entities)) {
                        if (!system.has(play.model.components.Antitrojan.class)) continue;
                        // skip if currently disabled
                        if (system.has(Disabled.class)) continue;

                        // get system position
                        Transform sysT = system.get(Transform.class);
                        if (sysT == null) continue;

                        // find nearest trojan seed within radius
                        for (Entity e : new ArrayList<>(entities)) {
                                if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                                Seed s = e.get(Seed.class);
                                if (!s.trojan) continue;
                                Transform st = e.get(Transform.class);
                                double dx = st.x - sysT.x;
                                double dy = st.y - sysT.y;
                                double dist2 = dx * dx + dy * dy;
                                if (dist2 <= GameBalance.ANTITROJAN_RADIUS * GameBalance.ANTITROJAN_RADIUS) {
                                        // clean it
                                        s.trojan = false;
                                        // disable the antitrojan system for cooldown seconds
                                        system.add(new Disabled(GameBalance.ANTITROJAN_DISABLE_SECONDS));
                                        // optionally play a sfx or post event via engine.event bus (not required)
                                        break; // only one cleanup per system this pass
                                }
                        }
                }
        }


        private Entity removeRandomFromLists(List<Entity> freeSquare, List<Entity> freeTriangle) {
                int total = freeSquare.size() + freeTriangle.size();
                if (total == 0) return null;
                int idx = rng.nextInt(total);
                if (idx < freeSquare.size()) {
                        return freeSquare.remove(idx);
                } else {
                        return freeTriangle.remove(idx - freeSquare.size());
                }
        }

        // ---------------------------
        // Utility / maintenance helpers
        // ---------------------------

        /**
         * If a PROTECTED seed was created from a VPN that later became disabled, revert it
         * back to its original base type and clear VPN metadata.
         */
        private void revertPacketsFromDisabledVpns() {
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.type != Seed.Type.PROTECTED) continue;
                        if (s.vpnConverter == null) continue;
                        if (!s.vpnConverter.has(Disabled.class)) continue;

                        // revert
                        Seed.Type original = s.protectedBaseType;
                        s.type = (original != null) ? original : Seed.Type.SQUARE;
                        s.protectedBaseType = null;
                        s.emulateType = null;
                        s.vpnConverter = null;
                        s.capacity = (s.type == Seed.Type.TRIANGLE) ? 4 : 3;
                }
        }

        private boolean isLinkFree(Link link) {
                if (link == null) return false;
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == link) return false;
                }
                return true;
        }

        private Seed.Type pickRandomMessengerType() {
                int r = rng.nextInt(3);
                if (r == 0) return Seed.Type.SQUARE;
                if (r == 1) return Seed.Type.TRIANGLE;
                return Seed.Type.INFINITE;
        }

        private static double clamp01(double v) {
                if (Double.isNaN(v)) return 0.0;
                if (v < 0.0) return 0.0;
                if (v > 1.0) return 1.0;
                return v;
        }
}

