package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces packets from EVERY OUT port of each system with a Producer.
 * Seed type derives from the OUT port shape. Kinematics are set for the first hop
 * based on the OUT port used (this is the "start port" for the hop).
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

                // Snapshot to avoid ConcurrentModification when we add seeds below
                List<Entity> snapshot = new ArrayList<>(entities);

                for (Entity sysE : snapshot) {
                        if (!sysE.has(Producer.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        // find all OUT ports of this system
                        List<Entity> outPorts = findOutPorts(sysE);
                        if (outPorts.isEmpty()) continue;

                        for (Entity outPort : outPorts) {
                                Entity freeLink = findFreeLinkFrom(outPort);
                                if (freeLink == null) continue;

                                PortInfo pinfo = outPort.get(PortInfo.class);
                                Seed.Type type = (pinfo.shape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                                Entity seedE = new Entity();
                                Seed s = new Seed(type);

                                // Per-hop kinematics based on OUT port shape used to spawn
                                applyKinematicsForHop(s, pinfo.shape);

                                // place at the OUT port
                                Transform pt = outPort.get(Transform.class);
                                seedE.add(new Transform(pt.x, pt.y));
                                seedE.add(s);

                                // attach to chosen link
                                s.currentLink = freeLink.get(Link.class);
                                s.progress = 0.0;

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

        /** Same rules as QueueSystem; shared here to avoid drift. */
        private void applyKinematicsForHop(Seed s, PortInfo.Shape outShape) {
                boolean compatibleStart =
                        (s.type == Seed.Type.SQUARE  && outShape == PortInfo.Shape.SQUARE) ||
                                (s.type == Seed.Type.TRIANGLE && outShape == PortInfo.Shape.TRIANGLE);

                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;
                        s.speed = compatibleStart ? base * 0.5 : base; // half when compatible
                        s.accel = 0.0;
                } else { // TRIANGLE
                        s.speed = 140.0;
                        s.accel = compatibleStart ? 0.0 : 220.0;       // accelerate when incompatible
                }
        }
}
