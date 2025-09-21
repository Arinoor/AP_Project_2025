package play.model.systems;

import play.model.audio.AudioAssets;
import play.model.audio.AudioService;
import play.model.components.Link;
import play.model.components.Seed;
import play.model.components.Transform;
import play.model.core.Entity;
import play.model.engine.GameEngine;
import play.utils.WiringUtils;

import java.util.*;

/**
 * Size-aware collisions + AoE + pairwise cooldown + separation.
 * Also adds "noise" on every collision (independent of lateral/impact).
 * Plays a soft collision SFX (throttled) via AudioService.
 *
 * Polyline-compatible: all link-length and direction math is based on WiringUtils.
 */
public class CollisionSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;
        /** Snapshot of shop state (nullable) */
        private final ShopSystem.ShopState shop;

        /** Pixels per "unit" (affects collision radius); make smaller for stricter collisions. */
        private final double unit;
        /** Impact wave radius in pixels. */
        private final double IMPACT_RADIUS;

        private static final double OFFSET_FACTOR = 3.5;
        private static final double PAIR_COOLDOWN_SEC = 0.75;

        /** Noise added (in UNITS) to each seed per collision. */
        private static final double NOISE_PER_HIT_UNITS = 0.5;

        /** Optional audio service for SFX (may be null). */
        private final AudioService audio;
        private double sfxCooldown = 0.0; // seconds
        private static final double SFX_THROTTLE = 0.12; // 120ms between collision SFX

        /** Pair -> cooldown seconds. */
        private final Map<PairKey, Double> pairCooldowns = new HashMap<>();

        public CollisionSystem(GameEngine engine,
                               List<Entity> entities,
                               ShopSystem shopSystem,
                               double packetSize) {
                this(engine, entities, shopSystem, packetSize, null);
        }

        public CollisionSystem(GameEngine engine,
                               List<Entity> entities,
                               ShopSystem shopSystem,
                               double packetSize,
                               AudioService audioService) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
                // Keep your original semantics: unit is the pixel scale used for thresholds.
                this.unit = Math.max(4.0, packetSize);
                // Reasonable default for AoE radius based on visual size.
                this.IMPACT_RADIUS = Math.max(100.0, this.unit * 8.0);
                this.audio = audioService;
        }

        @Override
        public void update(double dt) {
                // Collisions globally disabled by shop -> skip entirely.
                if (shop != null && shop.disableCollisions) return;

                if (sfxCooldown > 0) sfxCooldown -= dt;

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

                // Collect seeds with transform
                List<Entity> seeds = new ArrayList<>();
                for (Entity e : entities) {
                        if (e.has(Seed.class) && e.has(Transform.class)) seeds.add(e);
                }

                final int n = seeds.size();
                boolean anyHitThisTick = false;

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

                                // Separate along links so they don't re-hit immediately (polyline-aware)
                                //separateAlongLinks(sa, ea, sb, eb, rSum, Math.sqrt(Math.max(1e-12, d2)));
                                applyCollisionLateral(sa, sb, rSum - Math.sqrt(d2));


                                // Pairwise cooldown
                                pairCooldowns.put(key, PAIR_COOLDOWN_SEC);

                                // AoE ripple (exclude colliding pair)
                                if (shop == null || !shop.disableImpactWaves) {
                                        double cx = (ta.x + tb.x) * 0.5;
                                        double cy = (ta.y + tb.y) * 0.5;
                                        double destroyRadius = Math.min(ra, rb) * 1.0;
                                        applyImpactWave(cx, cy, destroyRadius, ea, eb);
                                }

                                anyHitThisTick = true;
                        }
                }

                if (anyHitThisTick) tryPlayCollisionSfx();
        }

        private double radiusFor(Seed s) {
                final double sizeUnits = s.sizeUnits(); // 2 or 3
                return (sizeUnits * unit) * 0.5; // smaller => stricter collision
        }

        private void scheduleReset(Seed s) {
                s.impactEnergy = Math.max(s.impactEnergy, 1.0);
        }

        /**
         * Polyline-aware separation: move seeds backwards/forwards along their link
         * by an amount proportional to the overlap (converted from px to normalized progress).
         */
        private void separateAlongLinks(Seed sa, Entity ea, Seed sb, Entity eb, double rSum, double actualDist) {
                final double overlap = Math.max(0.0, rSum - actualDist);
                if (overlap <= 0) return;

                double extra = 0.5 * unit; // ~half a port in px

                if (sa.currentLink != null) {
                        Link la = sa.currentLink;
                        double len = Math.max(1e-6, WiringUtils.pathLength(la));
                        double dp = (overlap * 0.5 + extra) / len;
                        sa.progress = clamp01(sa.progress - dp);
                        // position update is handled by movement system on its next tick/render;
                        // no immediate transform set is needed here.
                }
                if (sb.currentLink != null) {
                        Link lb = sb.currentLink;
                        double len = Math.max(1e-6, WiringUtils.pathLength(lb));
                        double dp = (overlap * 0.5 + extra) / len;
                        sb.progress = clamp01(sb.progress + dp);
                }
        }

        private double clamp01(double v) { return (v < 0) ? 0 : (v > 1) ? 1 : v; }

        /**
         * Polyline-aware impact wave:
         *  - For each seed in radius, compute the local tangent of its link at its current progress.
         *  - Use the perpendicular of that tangent to compute lateral offset, scaled by distance.
         */
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

                        // Local tangent as finite difference around current progress
                        double[] tan = tangentAt(s.currentLink, s.progress);
                        double tx = tan[0], ty = tan[1];
                        double mag = Math.hypot(tx, ty);
                        if (mag < 1e-6) continue;

                        // Perpendicular to tangent (unit)
                        double px = -ty / mag, py = tx / mag;

                        // World dir from impact center
                        double wx = (dist < 1e-6) ? 0 : dx / dist, wy = (dist < 1e-6) ? 0 : dy / dist;

                        final double strength = (dist <= destroyRadius) ? 1.0 : Math.pow(1.0 - (dist / IMPACT_RADIUS), 2.0);
                        double perpComponent = wx * px + wy * py;
                        double offset = perpComponent * strength * (IMPACT_RADIUS * 0.08) * OFFSET_FACTOR;

                        s.lateral += offset;
                }
        }

        private void applyCollisionLateral(Seed sa, Seed sb, double overlap) {
                // Add lateral impulse to colliding packets with more force
                double impulse = overlap * 1.5; // Increased from 0.5

                // Apply in opposite directions to create separation
                sa.lateral += impulse;
                sb.lateral -= impulse; // Opposite direction
        }
        /**
         * Approximate tangent (dx,dy) on a polyline link at normalized t by sampling
         * two nearby points (arc-length parameterization).
         */
        private double[] tangentAt(Link l, double t) {
                final double eps = 1e-3; // small param step
                double t0 = clamp01(t - eps);
                double t1 = clamp01(t + eps);
                WiringUtils.Pt a = WiringUtils.pointAlongNormalized(l, t0);
                WiringUtils.Pt b = WiringUtils.pointAlongNormalized(l, t1);
                return new double[] { b.x - a.x, b.y - a.y };
        }

        private void tryPlayCollisionSfx() {
                play.model.settings.Settings s = play.model.settings.SettingsStore.load();
                if (!s.sfxEnabled) return;

                play.model.audio.AudioService svc = (audio != null) ? audio : play.model.audio.GlobalAudio.get();
                if (svc == null) return;
                if (shop != null && shop.disableCollisions) return;
                if (sfxCooldown > 0) return;

                svc.playSfx(AudioAssets.COLLISION);
                sfxCooldown = SFX_THROTTLE;
        }

        private static final class PairKey {
                private final Entity a, b;
                private final int hash;
                private PairKey(Entity a, Entity b) {
                        if (java.lang.System.identityHashCode(a) <= java.lang.System.identityHashCode(b)) {
                                this.a = a; this.b = b;
                        } else {
                                this.a = b; this.b = a;
                        }
                        this.hash = java.lang.System.identityHashCode(this.a) * 31 + java.lang.System.identityHashCode(this.b);
                }
                static PairKey of(Entity x, Entity y) { return new PairKey(x, y); }
                @Override public boolean equals(Object o) { return (o instanceof PairKey pk) && pk.a == a && pk.b == b; }
                @Override public int hashCode() { return hash; }
        }
}
