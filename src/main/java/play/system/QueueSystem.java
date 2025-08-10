package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QueueSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final Random rng = new Random(); // for random empty port choice

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

                        // collect empty (free) outgoing links of this system
                        List<Entity> candidates = new ArrayList<>();
                        for (Entity linkE : entities) {
                                if (!linkE.has(Link.class)) continue;
                                Link l = linkE.get(Link.class);
                                if (l.fromPort == null || !l.fromPort.has(PortInfo.class)) continue;
                                if (l.fromPort.get(PortInfo.class).parentSystem != systemE) continue;
                                if (isLinkFree(l)) candidates.add(linkE);
                        }
                        if (candidates.isEmpty()) continue;

                        Entity seedE = q.peek();
                        if (seedE == null || !seedE.has(Seed.class)) continue;
                        Seed s = seedE.get(Seed.class);

                        Entity best = pickBest(candidates, s);
                        if (best != null) {
                                q.pop();
                                Link l = best.get(Link.class);
                                s.currentLink = l; // attach component-to-component
                                s.progress = 0.0;

                                // place at fromPort position
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

        /**
         * Priority:
         * 1) Any compatible & empty outgoing link (random among them)
         * 2) Otherwise, random empty outgoing link
         */
        private Entity pickBest(List<Entity> links, Seed s) {
                List<Entity> compatible = new ArrayList<>();
                for (Entity linkE : links) {
                        PortInfo.Shape toShape = linkE.get(Link.class).toPort.get(PortInfo.class).shape;
                        if (matches(s, toShape)) compatible.add(linkE);
                }
                if (!compatible.isEmpty()) {
                        return compatible.get(rng.nextInt(compatible.size()));
                }
                return links.get(rng.nextInt(links.size()));
        }

        private boolean matches(Seed s, PortInfo.Shape shape) {
                return (s.type == Seed.Type.SQUARE && shape == PortInfo.Shape.SQUARE)
                        || (s.type == Seed.Type.TRIANGLE && shape == PortInfo.Shape.TRIANGLE);
        }
}
