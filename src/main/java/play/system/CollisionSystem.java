package play.system;

import play.audio.AudioManager;
import play.core.Entity;
import play.components.*;

import java.util.*;

/**
 * Size-aware global collisions + AoE impact ripple with:
 *  - Pairwise cooldown to prevent A<->B from colliding again for a short window.
 *  - Immediate separation along links to remove overlaps and avoid repeat hits.
 *  - Gentle lateral kick (OFFSET_FACTOR kept modest).
 */
public class CollisionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        /** Pixels per "unit" (ports/packets). */
        private final double unit;

        /** AoE radius in pixels. */
        private final double IMPACT_RADIUS;

        /** Lateral kick scale (kept modest). */
        private static final double OFFSET_FACTOR = 0.12;

        /** Suppress re-collision for the same pair for this many seconds. */
        private static final double PAIR_COOLDOWN_SEC = 0.75;

        /** Track pairwise cooldowns (identity-based). */
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

                                // Pairwise cooldown check
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

                                // Still overlapping? suppress if either is in "global" cooldown
                                if (sa.justCollided || sb.justCollided) continue;

                                // === Register collision ===
                                sa.collisions++;
                                sb.collisions++;
                                sa.justCollided = true;
                                sb.justCollided = true;

                                // Short per-seed flash/cooldown (decays in SeedMovementSystem)
                                scheduleReset(sa);
                                scheduleReset(sb);

                                // === Positional separation to avoid instant re-hit ===
                                // Push both along their own links in opposite directions by half the overlap (plus epsilon)
                                separateAlongLinks(sa, ea, sb, eb, rSum, Math.sqrt(Math.max(1e-12, d2)));

                                // === Pairwise cooldown so A<->B won't collide again for a bit ===
                                pairCooldowns.put(key, PAIR_COOLDOWN_SEC);

                                // AoE ripple (exclude the colliding pair)
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

        // ----- Helpers -----

        private double radiusFor(Seed s) {
                final double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;
                return (sizeUnits * unit) * 0.5;
        }

        private void scheduleReset(Seed s) {
                s.impactEnergy = Math.max(s.impactEnergy, 1.0);
        }

        /**
         * Push both packets apart ALONG THEIR LINKS to clear overlap so they don't collide again immediately.
         */
        private void separateAlongLinks(Seed sa, Entity ea, Seed sb, Entity eb, double rSum, double actualDist) {
                final double overlap = Math.max(0.0, rSum - actualDist);
                if (overlap <= 0) return;

                // Small extra epsilon to be safe
                double extra = 0.5 * unit; // ~half a port in pixels

                // For each seed, compute progress delta = (overlap/2 + epsilon) / link_length, opposite directions.
                if (sa.currentLink != null && ea.has(Transform.class)) {
                        Link la = sa.currentLink;
                        la.updateLength();
                        double len = Math.max(1e-6, la.length);
                        double dp = (overlap * 0.5 + extra) / len;

                        // Move one forward, one backward (choice is arbitrary but consistent)
                        sa.progress = clamp01(sa.progress - dp);
                }
                if (sb.currentLink != null && eb.has(Transform.class)) {
                        Link lb = sb.currentLink;
                        lb.updateLength();
                        double len = Math.max(1e-6, lb.length);
                        double dp = (overlap * 0.5 + extra) / len;

                        sb.progress = clamp01(sb.progress + dp);
                }
        }

        private double clamp01(double v) { return (v < 0) ? 0 : (v > 1) ? 1 : v; }

        /** Applies AoE to all other packets except the two that collided. */
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

                        // link perpendicular (unit vector)
                        double px = -ly / mag;
                        double py =  lx / mag;

                        // wave direction (from center outward)
                        double wx = (dist < 1e-6) ? 0 : dx / dist;
                        double wy = (dist < 1e-6) ? 0 : dy / dist;

                        // strength profile
                        final double strength;
                        if (dist <= destroyRadius) {
                                strength = 1.0;
                        } else {
                                double k = 1.0 - (dist / IMPACT_RADIUS);
                                strength = k * k;
                        }

                        // Project wave onto link's perpendicular for lateral deflection
                        double perpComponent = wx * px + wy * py;

                        // Gentler kick
                        double offset = perpComponent * strength * (IMPACT_RADIUS * 0.08) * OFFSET_FACTOR;

                        s.lateral += offset;
                }
        }

        // Identity-based pair key (stable for the lifetime of the Entity objects)
        private static final class PairKey {
                private final Entity a, b;
                private final int hash;

                private PairKey(Entity a, Entity b) {
                        // Ensure order-independent key (a,b) == (b,a)
                        if (java.lang.System.identityHashCode(a) <= java.lang.System.identityHashCode(b)) {
                                this.a = a; this.b = b;
                        } else {
                                this.a = b; this.b = a;
                        }
                        // Precompute hash on identity
                        this.hash = java.lang.System.identityHashCode(this.a) * 31 + java.lang.System.identityHashCode(this.b);
                }
                static PairKey of(Entity x, Entity y) { return new PairKey(x, y); }

                @Override public boolean equals(Object o) {
                        if (this == o) return true;
                        if (!(o instanceof PairKey)) return false;
                        PairKey pk = (PairKey) o;
                        return this.a == pk.a && this.b == pk.b;
                }
                @Override public int hashCode() { return hash; }
        }
}
