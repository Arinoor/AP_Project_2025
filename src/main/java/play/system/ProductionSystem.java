package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

public class ProductionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final double dtCap;

        public ProductionSystem(GameEngine engine, List<Entity> entities, double dtCap) {
                this.engine = engine;
                this.entities = entities;
                this.dtCap = dtCap <= 0 ? 0.2 : dtCap;
        }

        @Override
        public void update(double dt) {
                if (dt > dtCap) dt = dtCap;

                // snapshot to avoid CME while adding seeds
                List<Entity> snapshot = new ArrayList<>(entities);

                for (Entity sysE : snapshot) {
                        if (!sysE.has(Producer.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        // emit from every OUT port that has a free link
                        for (Entity outPort : findOutPorts(sysE)) {
                                Entity freeLink = findFreeLinkFrom(outPort);
                                if (freeLink == null) continue;

                                Link link = freeLink.get(Link.class);
                                PortInfo.Shape outShape = outPort.get(PortInfo.class).shape;

                                // seed type is the OUT port’s shape
                                Seed.Type type = (outShape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                                Entity seedE = new Entity();
                                Seed s = new Seed(type);

                                // spawn at port
                                Transform pt = outPort.get(Transform.class);
                                seedE.add(new Transform(pt.x, pt.y));
                                seedE.add(s);

                                // attach to link and set kinematics for THIS link
                                s.currentLink = link;
                                s.progress = 0.0;
                                applyKinematicsForLink(s, link);

                                engine.entities().add(seedE);
                                engine.incrementProduced();
                        }
                }
        }

        private List<Entity> findOutPorts(Entity systemE) {
                List<Entity> result = new ArrayList<>();
                for (Entity e : entities) {
                        if (!e.has(PortInfo.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        if (p.parentSystem == systemE && p.io == PortInfo.IO.OUT) result.add(e);
                }
                return result;
        }

        private Entity findFreeLinkFrom(Entity outPort) {
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (l.fromPort != outPort) continue;
                        if (isLinkFree(l)) return e;
                }
                return null;
        }

        private boolean isLinkFree(Link link) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false;
                }
                return true;
        }

        /** Squares: constant; compatible start = half speed. Triangles: accel on incompatible start. */
        static void applyKinematicsForLink(Seed s, Link l) {
                PortInfo.Shape fromShape = l.fromPort.get(PortInfo.class).shape;

                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;
                        boolean compatibleStart = (fromShape == PortInfo.Shape.SQUARE);
                        s.speed = compatibleStart ? base * 0.5 : base;
                        s.accel = 0.0;
                } else { // TRIANGLE
                        s.speed = 140.0;
                        boolean incompatibleStart = (fromShape != PortInfo.Shape.TRIANGLE);
                        s.accel = incompatibleStart ? 220.0 : 0.0;
                }
        }
}
