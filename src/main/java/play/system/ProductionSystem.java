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

                List<Entity> snapshot = new ArrayList<>(entities);
                for (Entity sysE : snapshot) {
                        if (!sysE.has(Producer.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        for (Entity outPort : findOutPorts(sysE)) {
                                Entity freeLinkE = findFreeLinkFrom(outPort);
                                if (freeLinkE == null) continue;

                                Link link = freeLinkE.get(Link.class);
                                PortInfo.Shape outShape = outPort.get(PortInfo.class).shape;
                                Seed.Type type = (outShape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                                // spawn seed at the port
                                Transform pt = outPort.get(Transform.class);
                                Entity seedE = new Entity();
                                Seed s = new Seed(type);
                                s.px = pt.x; s.py = pt.y;

                                // attach and configure vectors for this hop
                                s.currentLink = link;
                                configureVectorsForHop(s, link);

                                seedE.add(new Transform(s.px, s.py));
                                seedE.add(s);

                                engine.entities().add(seedE);
                                engine.incrementProduced();
                        }
                }
        }

        /* ===== helpers ===== */

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
        public static void configureVectorsForHop(Seed s, Link link) {
                Transform a = link.fromPort.get(Transform.class);
                Transform b = link.toPort.get(Transform.class);

                double dx = b.x - a.x, dy = b.y - a.y;
                double len = Math.hypot(dx, dy);
                if (len < 1e-6) { s.vx = s.vy = s.ax = s.ay = 0; return; }

                double ux = dx / len, uy = dy / len; // direction along the wire

                // decide base speed/accel by the OUT port shape (start port)
                PortInfo.Shape startShape = link.fromPort.get(PortInfo.class).shape;

                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;
                        boolean compatible = (startShape == PortInfo.Shape.SQUARE);
                        double speed = compatible ? base * 0.5 : base;
                        s.vx = ux * speed;
                        s.vy = uy * speed;
                        s.ax = 0.0; s.ay = 0.0;
                } else {
                        double speed = 140.0;
                        boolean incompatible = (startShape != PortInfo.Shape.TRIANGLE);
                        double accel = incompatible ? 220.0 : 0.0;
                        s.vx = ux * speed;
                        s.vy = uy * speed;
                        s.ax = ux * accel;
                        s.ay = uy * accel;
                }
                s.progress = 0.0;
                s.lateral = 0.0;
        }
}
