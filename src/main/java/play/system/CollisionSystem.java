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

        private static final double COLLISION_RADIUS = 12.0; // ~ packet size / 2
        private static final double IMPACT_RADIUS    = 180.0;
        private static final double DESTROY_RADIUS   = 28.0;
        private static final double OFFSET_FACTOR    = 0.20;

        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
        }

        @Override
        public void update(double dt) {
                if (shop != null && shop.disableCollisions) return;

                List<Entity> seeds = new ArrayList<>();
                for (Entity e : entities) if (e.has(Seed.class) && e.has(Transform.class)) seeds.add(e);

                int n = seeds.size();
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

                                double dx = tb.x - ta.x;
                                double dy = tb.y - ta.y;
                                double d2 = dx*dx + dy*dy;
                                if (d2 > (COLLISION_RADIUS*COLLISION_RADIUS)) continue;

                                // collide (respect per-frame cooldown)
                                if (sa.justCollided || sb.justCollided) continue;
                                sa.collisions++;
                                sb.collisions++;
                                sa.justCollided = true;
                                sb.justCollided = true;

                                // cooldown proxy via impactEnergy
                                scheduleReset(sa);
                                scheduleReset(sb);

                                // AoE ripple unless disabled
                                if (shop == null || !shop.disableImpactWaves) {
                                        double cx = (ta.x + tb.x) * 0.5;
                                        double cy = (ta.y + tb.y) * 0.5;
                                        applyImpactWave(cx, cy);
                                }

                                AudioManager.getInstance().playSfx("/sfx/collide.wav");
                        }
                }
        }

        private void scheduleReset(Seed s) {
                s.impactEnergy = Math.max(s.impactEnergy, 1.0);
        }

        private void applyImpactWave(double cx, double cy) {
                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;

                        Transform st = e.get(Transform.class);
                        double dx = st.x - cx;
                        double dy = st.y - cy;
                        double dist = Math.hypot(dx, dy);
                        if (dist > IMPACT_RADIUS) continue;

                        Link l = s.currentLink;
                        if (l == null || l.fromPort == null || l.toPort == null) continue;
                        if (!l.fromPort.has(Transform.class) || !l.toPort.has(Transform.class)) continue;

                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);
                        double lx = b.x - a.x, ly = b.y - a.y;
                        double mag = Math.hypot(lx, ly);
                        if (mag < 1e-6) continue;

                        // link perpendicular
                        double px = -ly / mag;
                        double py =  lx / mag;

                        // wave direction
                        double wx = (dist < 1e-6) ? 0 : dx / dist;
                        double wy = (dist < 1e-6) ? 0 : dy / dist;

                        double strength;
                        if (dist <= DESTROY_RADIUS) {
                                strength = 1.0;
                        } else {
                                double k = 1.0 - (dist / IMPACT_RADIUS);
                                strength = k * k;
                        }

                        double perpComponent = wx * px + wy * py;
                        double offset = perpComponent * strength * (IMPACT_RADIUS * 0.08) * OFFSET_FACTOR;
                        s.lateral += offset;
                }
        }
}
