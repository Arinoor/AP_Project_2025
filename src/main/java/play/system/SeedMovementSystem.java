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
        private static final double LATERAL_DAMP = 0.98;       // gentle drift decay
        private static final double IMPACT_COOLDOWN_OFF = 0.15; // when impactEnergy decays below this, collisions re-enable
        private static final double MIN_LINK_LEN = 1e-3;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
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

                        // progress advance with simple kinematics
                        double dist = s.speed * dt + 0.5 * s.accel * dt * dt;
                        double dp = dist / length;
                        s.progress += dp;

                        // decay impact energy and auto-clear collision guard
                        s.impactEnergy *= Math.pow(0.6, dt * 60.0);
                        if (s.impactEnergy < IMPACT_COOLDOWN_OFF) {
                                s.justCollided = false;
                        }

                        // shop: disable lateral on demand
                        if (shop != null && shop.disableLateral) s.lateral = 0.0;

                        // light lateral damping so drift slowly fades
                        s.lateral *= LATERAL_DAMP;

                        // death conditions
                        if (Math.abs(s.lateral) > MAX_LATERAL || s.collisions >= s.capacity) {
                                s.currentLink = null;
                                toRemove.add(e);
                                engine.incrementLost();
                                continue;
                        }

                        // arrival clamp (RoutingSystem will handle handoff this frame)
                        if (s.progress >= 1.0) s.progress = 1.0;

                        // place Transform on the line (renderers can add a lateral offset if desired)
                        Transform st = e.get(Transform.class);
                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);
                        double nx = a.x + (b.x - a.x) * s.progress;
                        double ny = a.y + (b.y - a.y) * s.progress;
                        st.x = nx;
                        st.y = ny;
                }

                // remove any "lost" seeds
                for (Entity e : toRemove) entities.remove(e);
        }
}
