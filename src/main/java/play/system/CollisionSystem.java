package play.system;

import play.audio.AudioManager;
import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Global packet collisions with size-aware radii.
 *
 * Packet sizes per design doc:
 *  - Square  : size = 2 units
 *  - Triangle: size = 3 units
 *
 * We treat packets as discs for collision testing where the visual size (in "units")
 * is mapped to pixels via a configurable "unit" value. This keeps physics
 * consistent whether ports/packets are drawn small or large.
 *
 * Behavior on collision (unchanged):
 *  - increment collisions on both seeds (budgeting handled in SeedMovementSystem)
 *  - set a brief "justCollided" guard (we piggy-back on impactEnergy decay)
 *  - emit an AoE ripple unless disabled in the shop
 */
public class CollisionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        /** Pixels per "unit" (ports/packets). Example: if one port ≈ 12px, unit=12. */
        private final double unit;

        /** AoE radius in pixels (scaled from unit); packets within this feel the ripple. */
        private final double IMPACT_RADIUS;

        /** Offset scaling (tune down to soften the ripple). */
        private static final double OFFSET_FACTOR = 0.20;

        // ===== Constructors =====

        /**
         * Backward compatible: assumes ~12 px per unit (typical port size).
         * Square radius ≈ 12 px, triangle radius ≈ 18 px, impact radius ≈ 180 px.
         */
        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem) {
                this(engine, entities, shopSystem, 12.0);
        }

        /**
         * Preferred: inject pixels-per-unit so physics scale with your visuals.
         * For example, if a port is ~12px wide, pass 12.0; if you render bigger, pass that.
         */
        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem, double pixelsPerUnit) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
                this.unit = Math.max(6.0, pixelsPerUnit);
                // Keep a fairly generous AoE so impacts feel meaningful at typical layouts.
                this.IMPACT_RADIUS = this.unit * 15.0; // e.g., 12 * 15 = 180 px (matches earlier behavior)
        }

        // ===== Update =====

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

                                // ---- Size-aware collision test ----
                                double ra = radiusFor(sa);          // radius of A
                                double rb = radiusFor(sb);          // radius of B
                                double rSum = ra + rb;              // collide when centers within sum of radii

                                double dx = tb.x - ta.x;
                                double dy = tb.y - ta.y;
                                double d2 = dx*dx + dy*dy;
                                if (d2 > (rSum * rSum)) continue;

                                // Guard: don't double-collide in the same instant
                                if (sa.justCollided || sb.justCollided) continue;

                                // Register the collision budgets per doc
                                sa.collisions++;
                                sb.collisions++;
                                sa.justCollided = true;
                                sb.justCollided = true;

                                // Light cooldown using impactEnergy (decayed in SeedMovementSystem)
                                scheduleReset(sa);
                                scheduleReset(sb);

                                // AoE ripple unless shop disables impact waves
                                if (shop == null || !shop.disableImpactWaves) {
                                        double cx = (ta.x + tb.x) * 0.5;
                                        double cy = (ta.y + tb.y) * 0.5;

                                        // Strong (destroy-zone-like) near the smaller packet core
                                        double destroyRadius = Math.min(ra, rb) * 1.0; // ~packet half-size scaled
                                        applyImpactWave(cx, cy, destroyRadius);
                                }

                                // SFX (ensure resource path is correct in your jar/resources)
                                AudioManager.getInstance().playSfx("/sfx/collide.wav");
                        }
                }
        }

        // ===== Helpers =====

        /** Map seed type to a collision radius based on the doc sizes: square=2 units, triangle=3 units. */
        private double radiusFor(Seed s) {
                // Convert "size in units" to radius in pixels (size/2 * unit)
                final double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;
                return (sizeUnits * unit) * 0.5;
        }

        private void scheduleReset(Seed s) {
                // Cheap cooldown via impactEnergy; SeedMovementSystem decays it quickly.
                s.impactEnergy = Math.max(s.impactEnergy, 1.0);
        }

        private void applyImpactWave(double cx, double cy, double destroyRadius) {
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

                        // Strength profile:
                        //  - 1.0 inside "destroy zone" (near collision center; strongest impulse)
                        //  - Quadratic falloff to 0.0 at IMPACT_RADIUS
                        final double strength;
                        if (dist <= destroyRadius) {
                                strength = 1.0;
                        } else {
                                double k = 1.0 - (dist / IMPACT_RADIUS);
                                strength = k * k;
                        }

                        // Project wave onto link's perpendicular for lateral deflection
                        double perpComponent = wx * px + wy * py;

                        // Lateral offset proportional to AoE and strength.
                        double offset = perpComponent * strength * (IMPACT_RADIUS * 0.08) * OFFSET_FACTOR;

                        // Apply lateral "kick"
                        s.lateral += offset;
                }
        }
}
