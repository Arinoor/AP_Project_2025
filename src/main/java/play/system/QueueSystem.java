package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

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

                        // gather candidate outgoing links
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

                        Entity best = pickBest(candidates, s);
                        if (best != null) {
                                q.pop();
                                Link l = best.get(Link.class);
                                s.currentLink = l; // NOTE: component-to-component
                                s.progress = 0.0;

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
                for (Entity linkE : links) {
                        PortInfo.Shape toShape = linkE.get(Link.class).toPort.get(PortInfo.class).shape;
                        if (matches(s, toShape)) return linkE;
                }
                return links.get(0);
        }

        private boolean matches(Seed s, PortInfo.Shape shape) {
                return (s.type == Seed.Type.SQUARE && shape == PortInfo.Shape.SQUARE)
                        || (s.type == Seed.Type.TRIANGLE && shape == PortInfo.Shape.TRIANGLE);
        }
}
