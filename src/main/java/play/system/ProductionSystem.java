package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces packets from EVERY OUT port of each system that has a Producer component.
 * This ensures mixed shapes are generated when a system exposes multiple OUT ports
 * (e.g., one SQUARE and one TRIANGLE).
 *
 * Notes:
 * - We iterate over a snapshot of entities to avoid ConcurrentModification while
 *   adding new seed entities to the engine list.
 * - For each OUT port, we spawn at most one packet per "interval" if there is
 *   a free link leaving that port.
 * - Seed type is determined by the port's shape.
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

                // Snapshot to avoid ConcurrentModification when we add seeds below.
                List<Entity> snapshot = new ArrayList<>(entities);

                for (Entity sysE : snapshot) {
                        if (!sysE.has(Producer.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        // Find ALL OUT ports of this system.
                        List<Entity> outPorts = findOutPorts(sysE);
                        if (outPorts.isEmpty()) continue;

                        // Try to produce one seed for each OUT port that has a free link.
                        for (Entity outPort : outPorts) {
                                Entity freeLink = findFreeLinkFrom(outPort);
                                if (freeLink == null) continue;

                                PortInfo pinfo = outPort.get(PortInfo.class);
                                Seed.Type type = (pinfo.shape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                                Entity seedE = new Entity();
                                Seed s = new Seed(type);

                                // Speed/accel rules per doc:
                                // - Square: constant speed per port; from compatible start is half incompatible start.
                                // - Triangle: constant speed from compatible; accelerates when passing incompatible.
                                boolean compatibleStart = (type == Seed.Type.SQUARE && pinfo.shape == PortInfo.Shape.SQUARE)
                                        || (type == Seed.Type.TRIANGLE && pinfo.shape == PortInfo.Shape.TRIANGLE);

                                if (type == Seed.Type.SQUARE) {
                                        double base = 120.0;
                                        s.speed = compatibleStart ? base * 0.5 : base; // half speed if compatible
                                        s.accel = 0.0;
                                } else {
                                        s.speed = 140.0;
                                        s.accel = compatibleStart ? 0.0 : 220.0; // accelerate on incompatible (will matter later)
                                }

                                // Place at the OUT port position
                                Transform pt = outPort.get(Transform.class);
                                seedE.add(new Transform(pt.x, pt.y));
                                seedE.add(s);

                                // Attach to the chosen link
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
                        if (p.parentSystem == systemE && p.io == PortInfo.IO.OUT) {
                                result.add(e);
                        }
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
                        if (e.get(Seed.class).currentLink == link) return false; // compare component-to-component
                }
                return true;
        }
}
