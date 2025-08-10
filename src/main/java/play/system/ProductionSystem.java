package play.system;

import play.core.Entity;
import play.components.*;

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

                // Buffer new seeds; apply after the iteration
                java.util.List<Entity> toAdd = new java.util.ArrayList<>();

                // Iterate over a snapshot to avoid CME
                for (Entity sysE : new java.util.ArrayList<>(entities)) {
                        if (!sysE.has(Producer.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        // find an OUT port
                        Entity outPort = findFirstOutPort(sysE);
                        if (outPort == null) continue;

                        // pick a free link leaving this port
                        Entity freeLink = findFreeLinkFrom(outPort);
                        if (freeLink == null) continue;

                        PortInfo pinfo = outPort.get(PortInfo.class);
                        Seed.Type type = (pinfo.shape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                        Entity seedE = new Entity();
                        Seed s = new Seed(type);

                        boolean compatibleStart = (type == Seed.Type.SQUARE && pinfo.shape == PortInfo.Shape.SQUARE)
                                || (type == Seed.Type.TRIANGLE && pinfo.shape == PortInfo.Shape.TRIANGLE);

                        if (type == Seed.Type.SQUARE) {
                                double base = 120.0;
                                s.speed = compatibleStart ? base * 1.1 : base * 0.9;
                                s.accel = 0.0;
                        } else {
                                double base = 140.0;
                                s.speed = compatibleStart ? base * 1.05 : base * 0.9;
                                s.accel = compatibleStart ? 0.0 : 220.0;
                        }

                        Transform pt = outPort.get(Transform.class);
                        seedE.add(new Transform(pt.x, pt.y));
                        seedE.add(s);

                        s.currentLink = freeLink.get(Link.class);
                        s.progress = 0.0;

                        toAdd.add(seedE);
                        engine.incrementProduced();
                }

                // Apply spawns after the loop (no CME)
                if (!toAdd.isEmpty()) {
                        entities.addAll(toAdd);          // entities == engine.entities()
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
                        if (isLinkFree(l)) return e;
                }
                return null;
        }

        private boolean isLinkFree(Link link) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false; // compare component-to-component
                }
                return true;
        }
}
