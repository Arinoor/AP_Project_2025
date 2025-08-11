package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        private static final double MAX_LATERAL = 24.0;
        private static final double LATERAL_DAMP = 0.98;
        private static final double IMPACT_COOLDOWN_OFF = 0.15;
        private static final double MIN_LINK_LEN = 1e-3;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem) {
                this.engine = engine;
                this.entities = entities;
                this.shop  = (shopSystem != null) ? shopSystem.getState() : null;
        }

        @Override
        public void update(double dt) {
                List<Entity> toRemove = new ArrayList<>();

                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;

                        Link l = s.currentLink;
                        l.updateLength();
                        double length = Math.max(MIN_LINK_LEN, l.length);

                        // progress advance with proper acceleration integration
                        double dist = s.speed * dt + 0.5 * s.accel * dt * dt;
                        s.progress += dist / length;

                        // <- important: actually update speed so triangles ramp up on incompatible links
                        s.speed += s.accel * dt;

                        // decay impact energy; re-enable collisions
                        s.impactEnergy *= Math.pow(0.6, dt * 60.0);
                        if (s.impactEnergy < IMPACT_COOLDOWN_OFF) s.justCollided = false;

                        // shop: disable lateral
                        if (shop != null && shop.disableLateral) s.lateral = 0.0;
                        s.lateral *= LATERAL_DAMP;

                        // death conditions
                        if (Math.abs(s.lateral) > MAX_LATERAL || s.collisions >= s.capacity) {
                                s.currentLink = null;
                                toRemove.add(e);
                                engine.incrementLost();
                                continue;
                        }

                        if (s.progress >= 1.0) s.progress = 1.0;

                        // place on link
                        Transform st = e.get(Transform.class);
                        Transform a  = l.fromPort.get(Transform.class);
                        Transform b  = l.toPort.get(Transform.class);
                        st.x = a.x + (b.x - a.x) * s.progress;
                        st.y = a.y + (b.y - a.y) * s.progress;
                }

                for (Entity e : toRemove) entities.remove(e);
        }
}
