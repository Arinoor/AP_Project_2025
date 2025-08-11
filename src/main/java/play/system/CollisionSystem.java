package play.system;

import play.audio.AudioManager;
import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

public class CollisionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        private final double unit;          // pixels per "unit"
        private final double IMPACT_RADIUS; // AoE radius in pixels

        // magnitude for velocity kick
        private static final double KICK_SCALE = 28.0;

        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem) {
                this(engine, entities, shopSystem, 12.0);
        }

        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem, double pixelsPerUnit) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
                this.unit = Math.max(6.0, pixelsPerUnit);
                this.IMPACT_RADIUS = this.unit * 15.0;
        }

        @Override
        public void update(double dt) {
                if (shop != null && shop.disableCollisions) return;

                List<Entity> seeds = new ArrayList<>();
                for (Entity e : entities) if (e.has(Seed.class) && e.has(Transform.class)) seeds.add(e);

                final int n = seeds.size();
                for (int i = 0; i < n; i++) {
                        Entity ea = seeds.get(i);
                        Transform ta = ea.get(Transform.class);
                        Seed sa = ea.get(Seed.class);
                        if (sa.currentLink == null) continue;

                        for (int j = i + 1; j < n; j++) {
                                Entity eb = seeds.get(j);
                                Transform tb = eb.get(Transform.class);
                                Seed sb = eb.get(Seed.class);
                                if (sb.currentLink == null) continue;

                                double ra = radiusFor(sa);
                                double rb = radiusFor(sb);
                                double rSum = ra + rb;

                                double dx = tb.x - ta.x;
                                double dy = tb.y - ta.y;
                                double d2 = dx*dx + dy*dy;
                                if (d2 > (rSum * rSum)) continue;

                                if (sa.justCollided || sb.justCollided) continue;

                                sa.collisions++; sb.collisions++;
                                sa.justCollided = true; sb.justCollided = true;
                                sa.impactEnergy = Math.max(sa.impactEnergy, 1.0);
                                sb.impactEnergy = Math.max(sb.impactEnergy, 1.0);

                                if (shop == null || !shop.disableImpactWaves) {
                                        double cx = (ta.x + tb.x) * 0.5;
                                        double cy = (ta.y + tb.y) * 0.5;

                                        double destroyRadius = Math.min(ra, rb);
                                        applyImpactVelocityKick(cx, cy, destroyRadius, ea, eb);
                                }

                                AudioManager.getInstance().playSfx("/sfx/collide.wav");
                        }
                }
        }

        private double radiusFor(Seed s) {
                final double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;
                return (sizeUnits * unit) * 0.5;
        }

        /** Give nearby packets a perpendicular velocity impulse (exclude the colliding pair). */
        private void applyImpactVelocityKick(double cx, double cy, double destroyRadius, Entity excludeA, Entity excludeB) {
                for (Entity e : entities) {
                        if (e == excludeA || e == excludeB) continue;
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;

                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;

                        Transform st = e.get(Transform.class);
                        double dx = st.x - cx;
                        double dy = st.y - cy;
                        double dist = Math.hypot(dx, dy);
                        if (dist > IMPACT_RADIUS) continue;

                        Link l = s.currentLink;
                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);
                        double lx = b.x - a.x, ly = b.y - a.y;
                        double mag = Math.hypot(lx, ly);
                        if (mag < 1e-6) continue;

                        // perpendicular unit of link
                        double px = -ly / mag;
                        double py =  lx / mag;

                        // outward wave direction
                        double wx = (dist < 1e-6) ? 0 : dx / dist;
                        double wy = (dist < 1e-6) ? 0 : dy / dist;

                        // strength profile (1 inside destroy radius, quad falloff to 0 at edge)
                        final double strength;
                        if (dist <= destroyRadius) {
                                strength = 1.0;
                        } else {
                                double k = 1.0 - (dist / IMPACT_RADIUS);
                                strength = k * k;
                        }

                        // project wave onto link's perpendicular -> kick direction
                        double perpComponent = wx * px + wy * py;
                        double kick = perpComponent * strength * KICK_SCALE;

                        // add velocity impulse
                        s.vx += px * kick;
                        s.vy += py * kick;
                }
        }
}
