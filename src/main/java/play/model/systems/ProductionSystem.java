package play.model.systems;

import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;
import play.model.physics.Kinematics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Produces packets from EVERY OUT port of each system with a Producer.
 * Allowed types by OUT port shape:
 *   - SQUARE ports:   SQUARE, INFINITE, SECURE
 *   - TRIANGLE ports: TRIANGLE, SECURE
 */
public class ProductionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final double dtCap;
        private final Random rng = new Random();

        public ProductionSystem(GameEngine engine, List<Entity> entities, double dtCap) {
                this.engine = engine;
                this.entities = entities;
                this.dtCap = dtCap <= 0 ? 0.2 : dtCap;
        }

        @Override
        public void update(double dt) {
                if (dt > dtCap) dt = dtCap;

                // Snapshot to avoid concurrent modifications while adding seeds
                List<Entity> snapshot = new ArrayList<>(entities);

                for (Entity sysE : snapshot) {
                        if (!sysE.has(Producer.class)) continue;
                        Producer prod = sysE.get(Producer.class);

                        if (!prod.hasAnyQuota()) continue;

                        prod.timer += dt;
                        if (prod.timer < prod.interval) continue;
                        prod.timer = 0.0;

                        // all OUT ports of this system
                        List<Entity> outPorts = findOutPorts(sysE);
                        if (outPorts.isEmpty()) continue;

                        for (Entity outPort : outPorts) {
                                PortInfo pinfo = outPort.get(PortInfo.class);

                                // Determine allowed seed types for this OUT port
                                Seed.Type[] allowed;
                                if (pinfo.shape == PortInfo.Shape.SQUARE) {
                                        allowed = new Seed.Type[]{ Seed.Type.SQUARE, Seed.Type.INFINITE, Seed.Type.SECURE };
                                } else { // TRIANGLE
                                        allowed = new Seed.Type[]{ Seed.Type.TRIANGLE, Seed.Type.SECURE };
                                }

                                // Filter allowed by available quota
                                List<Seed.Type> available = new ArrayList<>();
                                for (Seed.Type t : allowed) {
                                        if (hasQuota(prod, t)) available.add(t);
                                }
                                if (available.isEmpty()) continue;

                                // Pick a type (random among available for variety)
                                Seed.Type chosen = available.get(rng.nextInt(available.size()));

                                // Need a free link from this port
                                Entity freeLink = findFreeLinkFrom(outPort);
                                if (freeLink == null) continue;

                                // Build seed
                                Entity seedE = new Entity();
                                Seed s = new Seed(chosen);

                                // Per-hop kinematics based on OUT port shape used to spawn
                                Kinematics.applyForHop(s, pinfo.shape);

                                // place at the OUT port
                                Transform pt = outPort.get(Transform.class);
                                seedE.add(new Transform(pt.x, pt.y));
                                seedE.add(s);

                                // attach to chosen link
                                s.currentLink = freeLink.get(Link.class);
                                s.progress = 0.0;

                                entities.add(seedE);
                                engine.incrementProduced();

                                // Decrement quota if finite
                                decrementQuota(prod, chosen);
                        }
                }
        }

        private boolean hasQuota(Producer p, Seed.Type t) {
                switch (t) {
                        case SQUARE:   return p.remainingSquare   != 0;
                        case TRIANGLE: return p.remainingTriangle != 0;
                        case INFINITE: return p.remainingInfinite != 0;
                        case SECURE:   return p.remainingSecure   != 0;
                }
                return false;
        }

        private void decrementQuota(Producer p, Seed.Type t) {
                switch (t) {
                        case SQUARE:   if (p.remainingSquare   > 0) p.remainingSquare--;   break;
                        case TRIANGLE: if (p.remainingTriangle > 0) p.remainingTriangle--; break;
                        case INFINITE: if (p.remainingInfinite > 0) p.remainingInfinite--; break;
                        case SECURE:   if (p.remainingSecure   > 0) p.remainingSecure--;   break;
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
}
