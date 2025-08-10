package play.system;

import java.util.ArrayList;
import java.util.List;

import play.core.Entity;
import play.components.Seed;
import play.components.Link;
import play.components.Transform;

/**
 * Global collisions (any two packets, any links) + Impact Wave AoE.
 *
 * Rules (per project doc / prior impl):
 * - Any intersection between two seeds => both gain 1 "noise" (collisions++).
 * - Impact wave: from collision point, push seeds along the perpendicular
 *   of THEIR current link, scaled by distance falloff. Disabled if shop
 *   state disableImpactWaves is true.
 * - Loss if |lateral| > LATERAL_LIMIT or collisions >= capacity.
 * - Lateral smoothly decays to 0 over time (wave damping).
 */
public class CollisionSystem {

        private final GameEngine engine;
        private final List<Entity> world;
        private final ShopSystem shop;

        public CollisionSystem(GameEngine engine, List<Entity> world, ShopSystem shop) {
                this.engine = engine;
                this.world = world;
                this.shop = shop;
        }

        // Tunables (match your visual scale)
        private static final double COLLISION_RADIUS = 12.0;     // "touch" distance to trigger a collision
        private static final double IMPACT_RADIUS    = 180.0;    // AoE radius
        private static final double IMPACT_MAG       = 16.0;     // base magnitude of the wave
        private static final double LATERAL_LIMIT    = 24.0;     // max allowed |lateral|
        private static final double LATERAL_DAMP_S   = 4.0;      // per-second damping for lateral

        private static final class SeedState {
                final Entity e;
                final Seed s;
                final Link l;
                final Transform tfFrom;
                double x, y;    // world position
                double px, py;  // link perpendicular unit (-uy, +ux)
                SeedState(Entity e, Seed s, Link l, Transform tfFrom) {
                        this.e = e; this.s = s; this.l = l; this.tfFrom = tfFrom;
                }
        }

        public void update(double dt) {
                // Collect active seeds with geometry precomputed
                List<SeedState> active = new ArrayList<>();
                for (Entity e : world) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (!s.alive || s.currentLink == null) continue;

                        Link l = s.currentLink;
                        if (l.fromPort == null || !l.fromPort.has(Transform.class)) continue;

                        Transform tfFrom = l.fromPort.get(Transform.class);

                        SeedState st = new SeedState(e, s, l, tfFrom);

                        // Perp unit for the link
                        double ux = l.ux, uy = l.uy;
                        double px = -uy, py =  ux;

                        // Parametric position + lateral offset on the link
                        double along = Math.max(0.0, Math.min(1.0, s.progress)) * l.length;
                        st.x = tfFrom.x + ux * along + px * s.lateral;
                        st.y = tfFrom.y + uy * along + py * s.lateral;
                        st.px = px; st.py = py;

                        active.add(st);
                }

                // Pairwise collisions (global)
                int n = active.size();
                List<SeedState[]> collisions = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                        SeedState a = active.get(i);
                        for (int j = i + 1; j < n; j++) {
                                SeedState b = active.get(j);
                                double dx = a.x - b.x;
                                double dy = a.y - b.y;
                                double d2 = dx*dx + dy*dy;
                                if (d2 <= COLLISION_RADIUS * COLLISION_RADIUS) {
                                        a.s.collisions++;
                                        b.s.collisions++;
                                        collisions.add(new SeedState[]{a, b});
                                }
                        }
                }

                // Impact wave AoE from each collision point
                if (collisions.size() > 0 && (shop == null || !shop.getState().disableImpactWaves)) {
                        for (SeedState[] pair : collisions) {
                                SeedState a = pair[0], b = pair[1];
                                double cx = 0.5 * (a.x + b.x);
                                double cy = 0.5 * (a.y + b.y);

                                for (SeedState st : active) {
                                        // distance from collision point
                                        double dx = st.x - cx, dy = st.y - cy;
                                        double dist2 = dx*dx + dy*dy;
                                        if (dist2 <= 1e-9) {
                                                // sitting exactly on the point: small kick
                                                double delta = IMPACT_MAG * 0.25;
                                                st.s.lateral += delta; // push outwards arbitrarily
                                                continue;
                                        }
                                        double dist = Math.sqrt(dist2);
                                        if (dist > IMPACT_RADIUS) continue;

                                        // Direction from collision -> seed, project onto seed's PERP
                                        double ix = dx / dist, iy = dy / dist;
                                        double perpComponent = ix * st.px + iy * st.py;

                                        // Smooth falloff (quadratic)
                                        double t = 1.0 - (dist / IMPACT_RADIUS);
                                        double strength = IMPACT_MAG * t * t;

                                        double delta = perpComponent * strength;
                                        st.s.lateral += delta;
                                }
                        }
                }

                // Apply damping & loss rules
                double damp = Math.exp(-LATERAL_DAMP_S * Math.max(0, dt));
                for (SeedState st : active) {
                        Seed s = st.s;

                        // smooth decay to model "ripple fading"
                        s.lateral *= damp;

                        // loss conditions
                        if (Math.abs(s.lateral) > LATERAL_LIMIT || s.collisions >= s.capacity) {
                                s.alive = false;
                                s.currentLink = null;   // detach
                                engine.incrementLost();
                                // (optional) play sfx via your AudioManager here
                        }
                }
        }
}
