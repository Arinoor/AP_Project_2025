package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QueueSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private static final Random RNG = new Random();

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

                        // gather free outgoing links from this system
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

                        Entity chosen = pickByPriority(s, candidates);
                        if (chosen != null) {
                                q.pop();

                                Link l = chosen.get(Link.class);
                                s.currentLink = l;

                                // set position to start and configure vectors for this hop
                                Transform ft = l.fromPort.get(Transform.class);
                                s.px = ft.x; s.py = ft.y;
                                ProductionSystem.configureVectorsForHop(s, l);

                                // keep Transform in sync for rendering
                                if (seedE.has(Transform.class)) {
                                        Transform st = seedE.get(Transform.class);
                                        st.x = s.px; st.y = s.py;
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

        private Entity pickByPriority(Seed s, List<Entity> links) {
                PortInfo.Shape want = (s.type == Seed.Type.SQUARE) ? PortInfo.Shape.SQUARE : PortInfo.Shape.TRIANGLE;
                List<Entity> compat = new ArrayList<>();
                for (Entity linkE : links) {
                        if (linkE.get(Link.class).fromPort.get(PortInfo.class).shape == want) compat.add(linkE);
                }
                if (!compat.isEmpty()) return compat.get(RNG.nextInt(compat.size()));
                return links.get(RNG.nextInt(links.size()));
        }
}
