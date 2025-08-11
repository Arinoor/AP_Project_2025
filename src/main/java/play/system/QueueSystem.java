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

                        // collect free outgoing links from THIS system
                        List<Entity> candidates = new ArrayList<>();
                        for (Entity linkE : entities) {
                                if (!linkE.has(Link.class)) continue;
                                Link l = linkE.get(Link.class);
                                if (l.fromPort == null || !l.fromPort.has(PortInfo.class)) continue;
                                if (l.fromPort.get(PortInfo.class).parentSystem != systemE) continue;
                                if (isLinkFree(l)) candidates.add(linkE);
                        }
                        if (candidates.isEmpty()) continue; // store until a link is free

                        Entity seedE = q.peek();
                        if (seedE == null || !seedE.has(Seed.class)) continue;
                        Seed s = seedE.get(Seed.class);

                        Entity chosen = pickByPriority(s, candidates);
                        if (chosen != null) {
                                q.pop();
                                Link l = chosen.get(Link.class);

                                // attach to link
                                s.currentLink = l;
                                s.progress = 0.0;
                                // per-link kinematics (rule: check the OUT port you start from)
                                ProductionSystem.applyKinematicsForLink(s, l);

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

        /** Priority: compatible OUT if free; otherwise pick a random free link. */
        private Entity pickByPriority(Seed s, List<Entity> links) {
                PortInfo.Shape want = (s.type == Seed.Type.SQUARE) ? PortInfo.Shape.SQUARE : PortInfo.Shape.TRIANGLE;

                List<Entity> compat = new ArrayList<>();
                for (Entity linkE : links) {
                        PortInfo.Shape outShape = linkE.get(Link.class).fromPort.get(PortInfo.class).shape;
                        if (outShape == want) compat.add(linkE);
                }
                if (!compat.isEmpty()) return compat.get(RNG.nextInt(compat.size()));
                return links.get(RNG.nextInt(links.size()));
        }
}
