package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dequeues from device input ports and assigns a free outgoing link.
 * Selection:
 *  - Prefer an empty link whose OUT port shape matches the seed type.
 *  - Otherwise pick a random empty link among the rest.
 * Kinematics are recomputed per hop based on the chosen OUT port shape.
 */
public class QueueSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = engine;
                this.entities = entities;
        }

        @Override
        public void update(double dt) {
                for (Entity portE : entities) {
                        if (!portE.has(PortInfo.class) || !portE.has(Queue.class)) continue;
                        PortInfo pinfo = portE.get(PortInfo.class);
                        if (pinfo.io != PortInfo.IO.IN) continue;

                        Queue q = portE.get(Queue.class);
                        if (q.isEmpty()) continue;

                        Entity systemE = pinfo.parentSystem;
                        if (systemE == null) continue;

                        // Gather candidate outgoing links from this device
                        List<Entity> candidates = new ArrayList<>();
                        for (Entity linkE : entities) {
                                if (!linkE.has(Link.class)) continue;
                                Link l = linkE.get(Link.class);
                                if (l.fromPort == null) continue;
                                if (!l.fromPort.has(PortInfo.class)) continue;
                                if (l.fromPort.get(PortInfo.class).parentSystem != systemE) continue;
                                if (isLinkFree(l)) candidates.add(linkE);
                        }
                        if (candidates.isEmpty()) continue;

                        Entity seedE = q.peek();
                        if (seedE == null || !seedE.has(Seed.class)) continue;
                        Seed s = seedE.get(Seed.class);

                        Entity chosen = pickBest(candidates, s);
                        if (chosen != null) {
                                q.pop();
                                Link l = chosen.get(Link.class);
                                s.currentLink = l;
                                s.progress = 0.0;

                                // ---- Per-hop kinematics based on OUT port shape
                                PortInfo.Shape outShape = l.fromPort.get(PortInfo.class).shape;
                                applyKinematicsForHop(s, outShape);

                                // position at fromPort
                                if (seedE.has(Transform.class) && l.fromPort.has(Transform.class)) {
                                        Transform st = seedE.get(Transform.class);
                                        Transform ft = l.fromPort.get(Transform.class);
                                        st.x = ft.x; st.y = ft.y;
                                }
                        }
                }
        }

        private boolean isLinkFree(Link link) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false;
                }
                return true;
        }

        private Entity pickBest(List<Entity> links, Seed s) {
                List<Entity> compat = new ArrayList<>();
                List<Entity> other  = new ArrayList<>();

                for (Entity linkE : links) {
                        Link l = linkE.get(Link.class);
                        PortInfo.Shape outShape = l.fromPort.get(PortInfo.class).shape;
                        boolean matches = (s.type == Seed.Type.SQUARE && outShape == PortInfo.Shape.SQUARE)
                                || (s.type == Seed.Type.TRIANGLE && outShape == PortInfo.Shape.TRIANGLE);
                        if (matches) compat.add(linkE);
                        else other.add(linkE);
                }

                ThreadLocalRandom rng = ThreadLocalRandom.current();
                if (!compat.isEmpty()) {
                        return compat.get(rng.nextInt(compat.size()));
                }
                return other.isEmpty() ? null : other.get(rng.nextInt(other.size()));
        }

        /** Applies per-hop rules from the spec. */
        private void applyKinematicsForHop(Seed s, PortInfo.Shape outShape) {
                boolean compatibleStart =
                        (s.type == Seed.Type.SQUARE  && outShape == PortInfo.Shape.SQUARE) ||
                                (s.type == Seed.Type.TRIANGLE && outShape == PortInfo.Shape.TRIANGLE);

                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;
                        s.speed = compatibleStart ? base * 0.5 : base; // half speed if compatible start
                        s.accel = 0.0;
                } else { // TRIANGLE
                        s.speed = 140.0;
                        s.accel = compatibleStart ? 0.0 : 220.0; // accelerate on incompatible start
                }
        }
}
