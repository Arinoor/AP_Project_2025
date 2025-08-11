package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        // how many pixels ≈ one "unit" (port size)
        private final double pixelsPerUnit;

        // pull-back & damping tuned to be forgiving
        private static final double PERP_SPRING = 22.0;   // stronger spring toward wire
        private static final double PERP_DAMP   = 0.92;   // more damping of perpendicular vel

        private static final double ENERGY_DECAY_60FPS = 0.6;
        private static final double IMPACT_COOLDOWN_OFF = 0.15;

        // give more room before loss so one hit won't eject
        private static final double THRESH_FACTOR = 1.75;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem) {
                this(engine, entities, shopSystem, 12.0);
        }

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem, double pixelsPerUnit) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
                this.pixelsPerUnit = Math.max(6.0, pixelsPerUnit);
        }

        @Override
        public void update(double dt) {
                List<Entity> toRemove = new ArrayList<>();

                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;

                        Link l = s.currentLink;
                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);

                        double dx = b.x - a.x, dy = b.y - a.y;
                        double L = Math.hypot(dx, dy);
                        if (L < 1e-6) continue;

                        double ux = dx / L, uy = dy / L; // along-wire

                        // integrate world-space vectors
                        s.vx += s.ax * dt;
                        s.vy += s.ay * dt;
                        s.px += s.vx * dt;
                        s.py += s.vy * dt;

                        // project to wire
                        double apx = s.px - a.x, apy = s.py - a.y;
                        double projLen = apx * ux + apy * uy;   // signed distance along the wire
                        double u = projLen / L;
                        double pxOn = a.x + ux * projLen;
                        double pyOn = a.y + uy * projLen;

                        // perpendicular deviation
                        double perpX = s.px - pxOn;
                        double perpY = s.py - pyOn;
                        double perpDist = Math.hypot(perpX, perpY);

                        // softly pull back to wire + damp perpendicular velocity
                        if (perpDist > 1e-6) {
                                double fx = (-perpX / perpDist) * PERP_SPRING;
                                double fy = (-perpY / perpDist) * PERP_SPRING;
                                s.vx += fx * dt;
                                s.vy += fy * dt;

                                double vPerp = s.vx * (-uy) + s.vy * (ux); // component along perp unit (-uy, ux)
                                s.vx -= vPerp * (-uy) * (1 - PERP_DAMP);
                                s.vy -= vPerp * ( ux) * (1 - PERP_DAMP);
                        }

                        s.progress = Math.max(0.0, Math.min(1.0, u));
                        s.lateral  = perpDist;

                        // collision guard decay
                        s.impactEnergy *= Math.pow(ENERGY_DECAY_60FPS, dt * 60.0);
                        if (s.impactEnergy < IMPACT_COOLDOWN_OFF) s.justCollided = false;

                        // shop toggle
                        if (shop != null && shop.disableLateral) {
                                // hard snap to wire when disabled
                                s.px = pxOn; s.py = pyOn;
                                s.lateral = 0.0;
                        }

                        // size-aware lateral threshold (more generous)
                        double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;
                        double threshold = THRESH_FACTOR * sizeUnits * pixelsPerUnit;

                        if (s.lateral > threshold || s.collisions >= s.capacity) {
                                s.currentLink = null;
                                toRemove.add(e);
                                engine.incrementLost();
                                continue;
                        }

                        // arrival at end
                        if (u >= 1.0) {
                                s.px = b.x; s.py = b.y; // snap to end
                                s.progress = 1.0;
                        } else if (u <= 0.0) {
                                s.px = a.x; s.py = a.y;
                                s.progress = 0.0;
                        } // otherwise we leave it offset so the drift is visible

                        // sync Transform for rendering
                        Transform st = e.get(Transform.class);
                        st.x = s.px; st.y = s.py;
                }

                for (Entity e : toRemove) entities.remove(e);
        }
}
