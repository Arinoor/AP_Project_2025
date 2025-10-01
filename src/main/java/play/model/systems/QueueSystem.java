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
import play.model.components.Producer;
import play.model.components.Queue;
import play.model.components.Merge;
import play.model.components.Distribute;
import play.model.components.Transform;
import play.model.engine.GameEngine;
import play.model.constants.GameBalance;
import play.model.components.*;
import play.model.physics.Kinematics;
import play.model.components.Link;

import java.util.*;
import java.util.stream.Collectors;

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
 * Extended here:
 *  - HEAVY arrival effects (destroy queued packets, flip port shape, heavy pass counting and link destruction)
 *  - Distribute system: split HEAVY into BITPACKETs
 *  - Merge system: buffer BITPACKETs and after MERGE_WAIT_SECONDS merge into HEAVY
 */

public final class QueueSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;
        private final Map<Entity, Deque<Entity>> deviceQueues = new HashMap<>();
        private final Random rng = new Random();

        // Merge buffers: per-merge-system list of (seed entity + arrivalTime)
        private final Map<Entity, List<MergeEntry>> mergeBuffers = new HashMap<>();
        private double time = 0.0;

        // SECURE speed constants
        private static final double SECURE_BASE_SPEED = GameBalance.SQUARE_BASE_SPEED;
        private static final double SECURE_SLOW_SPEED = Math.max(40.0, SECURE_BASE_SPEED * 0.35);

        private static class MergeEntry {
                final Entity seedEntity;
                final double arrivedAt;
                MergeEntry(Entity e, double t) { seedEntity = e; arrivedAt = t; }
        }

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = Objects.requireNonNull(engine, "engine");
                this.entities = Objects.requireNonNull(entities, "entities");
        }

        @Override
        public void update(double dt) {
                time += dt;

                processDisabledTimers(dt);
                runAntitrojanSweep();   // <-- existing
                revertPacketsFromDisabledVpns();

                applySecureAdaptiveSlowdown();
                applyPerFrameJerk(dt);
                handleInfiniteWireCollisions();

                List<Entity> arrivals = collectArrivals();
                processArrivals(arrivals);

                // Process merge buffers: if enough time passed since first arrival, merge into HEAVY
                processMergeBuffers();

                flushAllDeviceQueues();
        }

        // ---------------------------
        // Merge processing
        // ---------------------------

        private void processMergeBuffers() {
                List<Entity> toEmit = new ArrayList<>();
                for (Map.Entry<Entity, List<MergeEntry>> e : new ArrayList<>(mergeBuffers.entrySet())) {
                        Entity mergeSystem = e.getKey();
                        List<MergeEntry> buffer = e.getValue();
                        if (buffer.isEmpty()) {
                                mergeBuffers.remove(mergeSystem);
                                continue;
                        }
                        double firstT = buffer.get(0).arrivedAt;
                        if (time >= firstT + GameBalance.MERGE_WAIT_SECONDS) {
                                // create HEAVY of size k
                                int k = buffer.size();
                                // remove buffered seed entities (bitpackets) from world
                                int color = 0;
                                List<Entity> removed = new ArrayList<>();
                                for (MergeEntry me : buffer) {
                                        Entity se = me.seedEntity;
                                        if (!entities.contains(se)) continue;
                                        if (se.has(Seed.class)) {
                                                Seed s = se.get(Seed.class);
                                                if (s.type == Seed.Type.BITPACKET && s.colorRgb != 0) color = s.colorRgb;
                                                entities.remove(se);
                                        }
                                }
                                // build heavy entity
                                Entity heavyE = new Entity();
                                Seed heavy = new Seed(Seed.Type.HEAVY);
                                heavy.heavySize = k;
                                heavy.colorRgb = (color != 0) ? color : rng.nextInt(0xFFFFFF);
                                heavy.speed = GameBalance.HEAVY_STRAIGHT_SPEED;
                                heavyE.add(new Transform(mergeSystem.get(Transform.class).x, mergeSystem.get(Transform.class).y));
                                heavyE.add(heavy);
                                // Put heavy into the system device queue so it will be dispatched normally
                                Deque<Entity> q = deviceQueues.computeIfAbsent(mergeSystem, k2 -> new ArrayDeque<>());
                                q.addLast(heavyE);
                                entities.add(heavyE);
                                mergeBuffers.remove(mergeSystem);
                        }
                }
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
         *
         * EXTENSIONS:
         *  - HEAVY: destroys queued packets in the target system, toggles port shape with prob,
         *           increments link heavy-pass and destroys link if needed.
         *  - Distribute: when HEAVY arrives at Distribute system, split into bitpackets and enqueue them.
         *  - Merge: when BITPACKET arrives at Merge system, buffer it for MERGE_WAIT_SECONDS then combined.
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

                        // Keep reference to the link entity before we null it
                        Link arrivedLink = link;

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

                        // -------------------
                        // HEAVY specific effects
                        // -------------------
                        if (s.type == Seed.Type.HEAVY) {
                                // 1) destroy queued packets in this deviceQueues entry (if any)
                                Deque<Entity> q = deviceQueues.get(system);
                                if (q != null && !q.isEmpty()) {
                                        int removed = 0;
                                        while (!q.isEmpty()) {
                                                Entity qE = q.pollFirst();
                                                if (qE != null && entities.contains(qE)) {
                                                        entities.remove(qE);
                                                        engine.incrementLost();
                                                        removed++;
                                                }
                                        }
                                        // also ensure map entry removed
                                        deviceQueues.remove(system);
                                }

                                // 2) increment heavy pass on the arrived link and remove link entity if reached max
                                // find the link entity wrapper
                                Entity linkEntity = null;
                                for (Entity e : entities) {
                                        if (!e.has(Link.class)) continue;
                                        if (e.get(Link.class) == arrivedLink) { linkEntity = e; break; }
                                }
                                if (linkEntity != null) {
                                        int passes = arrivedLink.incrementHeavyPassCount();
                                        if (passes >= GameBalance.HEAVY_MAX_PASSES) {
                                                // remove the link entity immediately
                                                entities.remove(linkEntity);
                                        }
                                }

                                // 3) toggle port shape with probability
                                if (rng.nextDouble() < GameBalance.HEAVY_PORT_TOGGLE_PROB) {
                                        PortInfo pi = inPort.get(PortInfo.class);
                                        if (pi != null) {
                                                if (pi.shape == PortInfo.Shape.SQUARE)
                                                        pi.shape = PortInfo.Shape.TRIANGLE;
                                                else
                                                        pi.shape = PortInfo.Shape.SQUARE;
                                        }
                                }

                                // 4) Distribute behavior: if system is a Distribute system, split into BITPACKETs
                                if (system != null && system.has(Distribute.class)) {
                                        int parts = Math.max(1, s.heavySize);
                                        int color = (s.colorRgb != 0) ? s.colorRgb : rng.nextInt(0xFFFFFF);
                                        // remove the heavy seed (we'll create bitpackets)
                                        entities.remove(seedEntity);
                                        engine.incrementLostBy(0); // no change to counters for conversion itself

                                        for (int i = 0; i < parts; i++) {
                                                Entity bp = new Entity();
                                                Seed bit = new Seed(Seed.Type.BITPACKET);
                                                bit.colorRgb = color;
                                                // bitpackets move at constant speed (tunable)
                                                bit.speed = 120.0;
                                                bp.add(new Transform(portTransform.x, portTransform.y));
                                                bp.add(bit);
                                                entities.add(bp);
                                                // enqueue into this device queue for dispatching like normal packets
                                                Deque<Entity> dq = deviceQueues.computeIfAbsent(system, k -> new ArrayDeque<>());
                                                dq.addLast(bp);
                                        }
                                        continue; // heavy converted to bitpackets -> already handled
                                }
                        }

                        // -------------------
                        // BITPACKET arriving to Merge
                        // -------------------
                        if (s.type == Seed.Type.BITPACKET && system != null && system.has(Merge.class)) {
                                // buffer the bitpacket for this merge system
                                mergeBuffers.computeIfAbsent(system, k -> new ArrayList<>()).add(new MergeEntry(seedEntity, time));
                                // we remove the bitpacket entity from world while buffered
                                entities.remove(seedEntity);
                                continue;
                        }

                        // -------------------
                        // Default: enqueue into device buffer (respect capacity)
                        // -------------------
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
                        case SQUARE: reward = 200; break;
                        case TRIANGLE: reward = 3; break;
                        case INFINITE: reward = 1; break;
                        case SECURE: reward = 3; break;
                        case PROTECTED: reward = 5; break;
                        case HEAVY: reward = Math.max(1, s.heavySize); break;
                        case BITPACKET: reward = 1; break;
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
                        case SECURE_PROTECTED:
                        case HEAVY:
                        case BITPACKET:
                                if (!freeSquare.isEmpty() || !freeTriangle.isEmpty()) {
                                        return removeRandomFromLists(freeSquare, freeTriangle);
                                }
                        case PROTECTED:
                                // PROTECTED emulateType determines preference
                                Seed.Type emu = (s.emulateType != null) ? s.emulateType : Seed.Type.SQUARE;
                                if (emu == Seed.Type.TRIANGLE) {
                                        if (!freeTriangle.isEmpty()) return freeTriangle.remove(0);
                                } else {
                                        if (!freeSquare.isEmpty()) return freeSquare.remove(0);
                                }
                                break;
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

        public void updateSystemPosition(Entity system, double newX, double newY) {
                Deque<Entity> queue = deviceQueues.get(system);
                if (queue != null) {
                        for (Entity packet : queue) {
                                if (packet.has(Transform.class)) {
                                        Transform t = packet.get(Transform.class);
                                        // Find which port this packet is associated with
                                        Entity port = findAssociatedPort(system, packet);
                                        if (port != null && port.has(Transform.class)) {
                                                Transform portTransform = port.get(Transform.class);
                                                t.x = portTransform.x;
                                                t.y = portTransform.y;
                                        }
                                }
                        }
                }
        }

        private Entity findAssociatedPort(Entity system, Entity packet) {
                if (packet.has(Seed.class)) {
                        Seed seed = packet.get(Seed.class);

                        // Check if seed has a current link
                        if (seed.currentLink != null) {
                                // For packets in transit, find which end of the link they're closest to
                                Link link = seed.currentLink;
                                Transform seedTransform = packet.get(Transform.class);

                                // Calculate distance to both ends of the link
                                Transform fromTransform = link.fromPort.get(Transform.class);
                                Transform toTransform = link.toPort.get(Transform.class);

                                double distToFrom = Math.hypot(
                                        seedTransform.x - fromTransform.x,
                                        seedTransform.y - fromTransform.y
                                );

                                double distToTo = Math.hypot(
                                        seedTransform.x - toTransform.x,
                                        seedTransform.y - toTransform.y
                                );

                                // Return the closer port that belongs to this system
                                if (distToFrom < distToTo) {
                                        PortInfo portInfo = link.fromPort.get(PortInfo.class);
                                        if (portInfo.parentSystem == system) {
                                                return link.fromPort;
                                        }
                                } else {
                                        PortInfo portInfo = link.toPort.get(PortInfo.class);
                                        if (portInfo.parentSystem == system) {
                                                return link.toPort;
                                        }
                                }
                        }

                        // For queued packets, find the port with matching position
                        Transform packetTransform = packet.get(Transform.class);
                        for (Entity entity : entities) {
                                if (entity.has(PortInfo.class) && entity.has(Transform.class)) {
                                        PortInfo portInfo = entity.get(PortInfo.class);
                                        Transform portTransform = entity.get(Transform.class);

                                        if (portInfo.parentSystem == system &&
                                                Math.abs(portTransform.x - packetTransform.x) < 20 &&
                                                Math.abs(portTransform.y - packetTransform.y) < 20) {
                                                return entity;
                                        }
                                }
                        }
                }
                return null;
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

