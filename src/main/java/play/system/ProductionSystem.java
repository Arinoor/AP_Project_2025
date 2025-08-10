package play.system;

import play.core.Entity;
import play.components.*;

import java.util.List;

/**
 * Spawns seeds at Producer systems and injects them into a free outgoing link.
 * Square: base speed 120; if compatible start -> speed 60 (half), accel 0
 * Triangle: speed 140; if incompatible start -> accel 220, else accel 0
 */
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

                for (Entity sysE : entities) {
                        if (!sysE.has(Producer.class) || sysE.has(Reference.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        // pick an OUT port from this system
                        Entity outPort = findFirstOutPort(sysE);
                        if (outPort == null) continue;

                        // find a free link leaving this system
                        Entity freeLink = findFreeLinkFrom(outPort);
                        if (freeLink == null) {
                                // fall back: queue into a compatible input if nothing free (optional)
                                continue;
                        }

                        // Seed type matches out port shape first else the other type (doc allows any)
                        PortInfo pinfo = outPort.get(PortInfo.class);
                        Seed.Type type = (pinfo.shape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                        Entity seedE = new Entity();
                        Seed s = new Seed(type);

                        // set kinematics per doc
                        boolean compatibleStart = (type == Seed.Type.SQUARE && pinfo.shape == PortInfo.Shape.SQUARE)
                                || (type == Seed.Type.TRIANGLE && pinfo.shape == PortInfo.Shape.TRIANGLE);
                        if (type == Seed.Type.SQUARE) {
                                double base = 120.0;
                                s.speed = compatibleStart ? base * 0.5 : base;
                                s.accel = 0.0;
                        } else {
                                s.speed = 140.0;
                                s.accel = compatibleStart ? 0.0 : 220.0;
                        }

                        // attach transform at port
                        Transform pt = outPort.get(Transform.class);
                        Transform st = new Transform(pt.x, pt.y);
                        seedE.add(st);

                        // attach components and inject onto link (NOTE currentLink is Link component, not Entity)
                        seedE.add(s);
                        s.currentLink = freeLink.get(Link.class);
                        s.progress = 0.0;

                        engine.entities().add(seedE);

                        // If your engine later exposes counters, you can increment here.
                        // engine.incrementProduced();
                }
        }

        private Entity findFirstOutPort(Entity systemE) {
                for (Entity e : entities) {
                        if (!e.has(PortInfo.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        if (p.parentSystem == systemE && p.io == PortInfo.IO.OUT) return e;
                }
                return null;
        }

        private Entity findFreeLinkFrom(Entity outPort) {
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (l.fromPort != outPort) continue;
                        if (isLinkFree(e)) return e;
                }
                return null;
        }

        private boolean isLinkFree(Entity linkE) {
                Link l = linkE.get(Link.class);
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == l) return false; // compare component-to-component
                }
                return true;
        }
}
