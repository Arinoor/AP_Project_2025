package play.model.systems;

import play.model.audio.AudioManager;
import play.model.components.Link;
import play.model.components.Seed;
import play.model.components.Transform;
import play.model.core.Entity;
import play.model.engine.GameEngine;

import java.util.*;

/**
 * Size-aware collisions + AoE + pairwise cooldown + separation.
 * Also adds "noise" on every collision (independent of lateral/impact).
 */
public class CollisionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        private final double unit;           // pixels per "unit" // make it less if you want harder condition for collision
        private final double IMPACT_RADIUS;  // px
        private static final double OFFSET_FACTOR = 0.12;
        private static final double PAIR_COOLDOWN_SEC = 0.75;

        /** Noise added (in UNITS) to each seed per collision. */
        private static final double NOISE_PER_HIT_UNITS = 0.5;

        private final Map<PairKey, Double> pairCooldowns = new HashMap<>();

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

                // Decay pair cooldowns
                if (!pairCooldowns.isEmpty()) {
                        Iterator<Map.Entry<PairKey, Double>> it = pairCooldowns.entrySet().iterator();
                        while (it.hasNext()) {
                                Map.Entry<PairKey, Double> e = it.next();
                                double left = e.getValue() - dt;
                                if (left <= 0) it.remove();
                                else e.setValue(left);
                        }
                }

                // Collect seeds
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

                                // Pairwise cooldown
                                PairKey key = PairKey.of(ea, eb);
                                Double cd = pairCooldowns.get(key);
                                if (cd != null && cd > 0) continue;

                                double ra = radiusFor(sa);
                                double rb = radiusFor(sb);
                                double rSum = ra + rb;

                                double dx = tb.x - ta.x;
                                double dy = tb.y - ta.y;
                                double d2 = dx*dx + dy*dy;
                                if (d2 > (rSum * rSum)) continue;

                                if (sa.justCollided || sb.justCollided) continue;

                                // Register collision
                                sa.collisions++;
                                sb.collisions++;
                                sa.justCollided = true;
                                sb.justCollided = true;

                                // Add NOISE to both (in units, not pixels)
                                sa.noise += NOISE_PER_HIT_UNITS;
                                sb.noise += NOISE_PER_HIT_UNITS;

                                // Short per-seed cooldown via impactEnergy (decayed in movement system)
                                scheduleReset(sa);
                                scheduleReset(sb);

                                // Separate along links so they don't re-hit immediately
                                separateAlongLinks(sa, ea, sb, eb, rSum, Math.sqrt(Math.max(1e-12, d2)));

                                // Pairwise cooldown
                                pairCooldowns.put(key, PAIR_COOLDOWN_SEC);

                                // AoE ripple (exclude colliding pair)
                                if (shop == null || !shop.disableImpactWaves) {
                                        double cx = (ta.x + tb.x) * 0.5;
                                        double cy = (ta.y + tb.y) * 0.5;
                                        double destroyRadius = Math.min(ra, rb) * 1.0;
                                        applyImpactWave(cx, cy, destroyRadius, ea, eb);
                                }

                                AudioManager.getInstance().playSfx("/sfx/collide.wav");
                        }
                }
        }

        private double radiusFor(Seed s) {
                final double sizeUnits = s.sizeUnits(); // 2 or 3
                return (sizeUnits * unit) * 0.5; // make less if you want harder condition for collision
        }

        private void scheduleReset(Seed s) {
                s.impactEnergy = Math.max(s.impactEnergy, 1.0);
        }

        private void separateAlongLinks(Seed sa, Entity ea, Seed sb, Entity eb, double rSum, double actualDist) {
                final double overlap = Math.max(0.0, rSum - actualDist);
                if (overlap <= 0) return;

                double extra = 0.5 * unit; // ~half a port in px

                if (sa.currentLink != null) {
                        Link la = sa.currentLink;
                        la.updateLength();
                        double len = Math.max(1e-6, la.length);
                        double dp = (overlap * 0.5 + extra) / len;
                        sa.progress = clamp01(sa.progress - dp);
                }
                if (sb.currentLink != null) {
                        Link lb = sb.currentLink;
                        lb.updateLength();
                        double len = Math.max(1e-6, lb.length);
                        double dp = (overlap * 0.5 + extra) / len;
                        sb.progress = clamp01(sb.progress + dp);
                }
        }

        private double clamp01(double v) { return (v < 0) ? 0 : (v > 1) ? 1 : v; }

        private void applyImpactWave(double cx, double cy, double destroyRadius, Entity excludeA, Entity excludeB) {
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

                        double px = -ly / mag, py = lx / mag; // link perpendicular
                        double wx = (dist < 1e-6) ? 0 : dx / dist, wy = (dist < 1e-6) ? 0 : dy / dist;

                        final double strength = (dist <= destroyRadius) ? 1.0 : Math.pow(1.0 - (dist / IMPACT_RADIUS), 2.0);
                        double perpComponent = wx * px + wy * py;
                        double offset = perpComponent * strength * (IMPACT_RADIUS * 0.08) * OFFSET_FACTOR;

                        s.lateral += offset;
                }
        }

        private static final class PairKey {
                private final Entity a, b;
                private final int hash;
                private PairKey(Entity a, Entity b) {
                        if (java.lang.System.identityHashCode(a) <= java.lang.System.identityHashCode(b)) { this.a = a; this.b = b; }
                        else { this.a = b; this.b = a; }
                        this.hash = java.lang.System.identityHashCode(this.a) * 31 + java.lang.System.identityHashCode(this.b);
                }
                static PairKey of(Entity x, Entity y) { return new PairKey(x, y); }
                @Override public boolean equals(Object o) { return (o instanceof PairKey pk) && pk.a == a && pk.b == b; }
                @Override public int hashCode() { return hash; }
        }
}
