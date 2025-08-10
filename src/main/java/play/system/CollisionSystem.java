package play.system;

import play.audio.AudioManager;
import play.components.Link;
import play.components.PortInfo;
import play.components.Seed;
import play.components.Transform;
import play.core.Entity;

import java.util.ArrayList;
import java.util.List;

public class CollisionSystem implements System {
        private final List<Entity> entities;
        private final GameEngine engine;
        private final ShopSystem shop;

        // Tunables (pick sensible values per doc)
        private static final double CONTACT_RADIUS = 14.0;   // touch distance
        private static final double SEPARATION_K   = 0.2;    // push strength
        private static final double IMPACT_RADIUS  = 180.0;  // AoE reach
        private static final double IMPACT_K       = 22.0;   // AoE magnitude
        private static final double OFFWIRE_LIMIT  = 24.0;   // lateral limit before loss

        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shop) {
                this.entities = entities;
                this.engine = engine;
                this.shop = shop;
        }

        @Override
        public void update(double dt) {
                if (shop != null && shop.getState().disableCollisions) return;

                List<Entity> seeds = new ArrayList<>();
                for (Entity e : entities) if (e.has(Seed.class) && e.has(Transform.class)) seeds.add(e);

                List<Entity> toRemove = new ArrayList<>();

                for (int i = 0; i < seeds.size(); i++) {
                        Entity ea = seeds.get(i);
                        Seed sa = ea.get(Seed.class);
                        Transform ta = ea.get(Transform.class);

                        for (int j = i + 1; j < seeds.size(); j++) {
                                Entity eb = seeds.get(j);
                                Seed sb = eb.get(Seed.class);
                                Transform tb = eb.get(Transform.class);

                                double dx = ta.x - tb.x, dy = ta.y - tb.y;
                                double d = Math.hypot(dx, dy);
                                if (d >= CONTACT_RADIUS) continue;

                                // direct collision: add noise, separate along local axis
                                sa.collisions++; sb.collisions++;
                                double push = (CONTACT_RADIUS - d) * SEPARATION_K;
                                if (d == 0) { sa.lateral +=  push; sb.lateral += -push; }
                                else {
                                        double ux = dx / d;
                                        sa.lateral +=  ux * push;
                                        sb.lateral += -ux * push;
                                }

                                // AoE impact wave
                                if (shop == null || !shop.getState().disableImpactWaves) {
                                        double cx = (ta.x + tb.x) * 0.5;
                                        double cy = (ta.y + tb.y) * 0.5;

                                        for (Entity e : seeds) {
                                                if (e == ea || e == eb) continue;
                                                Seed s = e.get(Seed.class);
                                                if (s.currentLink == null) continue; // no effect inside devices

                                                Transform ts = e.get(Transform.class);
                                                double px = ts.x - cx, py = ts.y - cy;
                                                double dist = Math.hypot(px, py);
                                                if (dist <= 0.0001 || dist > IMPACT_RADIUS) continue;

                                                // Falloff ~ (1 - r/R)^2
                                                double strength = IMPACT_K * Math.pow(1.0 - dist / IMPACT_RADIUS, 2);

                                                // Project impact direction onto PERP of current link => lateral impulse
                                                Entity linkE = s.currentLink;
                                                Link L = linkE.get(Link.class);
                                                Transform A = L.fromPort.get(Transform.class);
                                                Transform B = L.toPort.get(Transform.class);
                                                double vx = B.x - A.x, vy = B.y - A.y;
                                                double mag = Math.hypot(vx, vy);
                                                if (mag <= 0.0001) continue;
                                                double pxn = -vy / mag, pyn = vx / mag;    // link perpendicular
                                                double ixn = px / dist, iyn = py / dist;   // impact direction
                                                double perpComponent = ixn * pxn + iyn * pyn;

                                                s.lateral += perpComponent * strength * dt; // smooth add
                                        }
                                }
                        }
                }

                // losses from off-wire or capacity
                for (Entity e : seeds) {
                        Seed s = e.get(Seed.class);
                        if (Math.abs(s.lateral) > OFFWIRE_LIMIT || s.collisions >= s.capacity) {
                                s.speed = -1;                // mark dead path
                                toRemove.add(e);
                                engine.incrementLost();
                                AudioManager.getInstance().playSfx("sfx/packetloss.wav");
                        }
                }
                entities.removeAll(toRemove);
        }
}
