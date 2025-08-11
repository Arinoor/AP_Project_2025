package play.system;

import play.core.Entity;
import play.components.*;

import java.util.*;

/**
 * Handles:
 *  - Detect arrivals (progress >= 1.0) into the target system.
 *  - If target system is a Reference: consume + score.
 *  - Otherwise buffer per-device (capacity 5) and flush to any free OUT link.
 *    Preference: a free, compatible OUT link; else any free OUT link at random.
 *  - Applies per-hop kinematics on dispatch (triangle accel rule, square half-speed on compatible).
 *
 * This replaces the old RoutingSystem.
 */
public class QueueSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;

        /** Per-device buffer (queue) of waiting seed entities. */
        private final Map<Entity, Deque<Entity>> deviceQueues = new HashMap<>();

        /** Storage capacity per device (phase rule). */
        private static final int DEVICE_CAPACITY = 5;

        private final Random rng = new Random();

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = engine;
                this.entities = entities;
        }

        @Override
        public void update(double dt) {
                // 1) Collect arrivals this frame
                List<Entity> arrivals = new ArrayList<>();
                for (Entity e : new ArrayList<>(entities)) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;
                        if (s.progress < 1.0) continue;

                        arrivals.add(e);
                }

                // 2) Process arrivals
                for (Entity seedE : arrivals) {
                        Seed s = seedE.get(Seed.class);
                        Link link = s.currentLink;
                        Entity inPort = link.toPort;
                        PortInfo pTo = inPort.get(PortInfo.class);
                        Entity system = pTo.parentSystem;

                        // snap to the IN port position
                        Transform st = seedE.get(Transform.class);
                        Transform tIn = inPort.get(Transform.class);
                        st.x = tIn.x;
                        st.y = tIn.y;

                        // clear travel state (now "inside" the system)
                        s.currentLink = null;
                        s.progress = 0.0;
                        s.lateral = 0.0;          // optionally reset lateral on entry

                        // currency for EACH packet entering a system
                        if (s.type == Seed.Type.SQUARE) {
                                engine.incrementCoins(1);
                        } else if (s.type == Seed.Type.TRIANGLE) {
                                engine.incrementCoins(2);
                        }
                        // Reference systems consume (do not forward)
                        if (system.has(Reference.class)) {
                                engine.incrementReachedReference();
                                engine.notifySeedDelivered(s);
                                entities.remove(seedE);
                                continue;
                        }

                        // Enqueue into the device buffer
                        Deque<Entity> buf = deviceQueues.computeIfAbsent(system, k -> new ArrayDeque<>());
                        if (buf.size() >= DEVICE_CAPACITY) {
                                // storage overflow => packet loss
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

                                // Preferred compatible link
                                Entity chosenLinkE = null;
                                if (s.type == Seed.Type.SQUARE && !freeSquare.isEmpty()) {
                                        chosenLinkE = freeSquare.remove(0);
                                } else if (s.type == Seed.Type.TRIANGLE && !freeTriangle.isEmpty()) {
                                        chosenLinkE = freeTriangle.remove(0);
                                }

                                // If no compatible link free, try any free link (random)
                                if (chosenLinkE == null) {
                                        int total = freeSquare.size() + freeTriangle.size();
                                        if (total > 0) {
                                                int idx = rng.nextInt(total);
                                                chosenLinkE = (idx < freeSquare.size())
                                                        ? freeSquare.remove(idx)
                                                        : freeTriangle.remove(idx - freeSquare.size());
                                        }
                                }

                                // If still nothing, stop trying this device (it's congested)
                                if (chosenLinkE == null) break;

                                // We can dispatch this seed
                                buf.removeFirst();

                                Link l = chosenLinkE.get(Link.class);
                                PortInfo.Shape outShape = l.fromPort.get(PortInfo.class).shape;

                                // per-hop kinematics
                                applyKinematicsForHop(s, outShape);

                                // place at OUT port and start hop
                                Transform tFrom = l.fromPort.get(Transform.class);
                                Transform st = seedE.get(Transform.class);
                                st.x = tFrom.x;
                                st.y = tFrom.y;

                                s.currentLink = l;
                                s.progress = 0.0;

                                progressed = true;
                        }
                }
        }

        private boolean isLinkFree(Link link) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == link) return false; // one seed per wire at a time
                }
                return true;
        }

        /** Per-hop rules from the spec. */
        private void applyKinematicsForHop(Seed s, PortInfo.Shape outShape) {
                boolean compatibleStart =
                        (s.type == Seed.Type.SQUARE  && outShape == PortInfo.Shape.SQUARE) ||
                                (s.type == Seed.Type.TRIANGLE && outShape == PortInfo.Shape.TRIANGLE);

                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;
                        s.speed = compatibleStart ? base * 0.5 : base; // half when compatible
                        s.accel = 0.0;
                } else {
                        s.speed = 140.0;
                        s.accel = compatibleStart ? 0.0 : 220.0;       // accelerate when incompatible
                }
        }
}
