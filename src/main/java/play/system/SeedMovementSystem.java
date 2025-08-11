package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Advances seeds, applies acceleration, and removes seeds only when
 * (a) lateral exceeds a size-aware threshold OR (b) collision budget exhausted.
 * More tolerant constants so single impacts don't cause instant losses.
 */
public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        // Visual/physics scale: how many pixels equals one "unit" (a port)
        private final double pixelsPerUnit;

        // Gentle drift decay
        private static final double LATERAL_DAMP = 0.985;

        // When impactEnergy decays below this, collisions re-enable (raised for longer guard)
        private static final double IMPACT_COOLDOWN_OFF = 0.35;

        private static final double MIN_LINK_LEN = 1e-3;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem, double pixelsPerUnit) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
                this.pixelsPerUnit = Math.max(6.0, pixelsPerUnit);
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

                        // Proper kinematics: dv = a*dt, then integrate distance
                        s.speed += s.accel * dt;
                        if (s.speed < 0) s.speed = 0;

                        double dist = s.speed * dt + 0.5 * s.accel * dt * dt;
                        double dp = dist / length;
                        s.progress += dp;

                        // decay impact flash and auto-clear collision guard
                        s.impactEnergy *= Math.pow(0.6, dt * 60.0);
                        if (s.impactEnergy < IMPACT_COOLDOWN_OFF) {
                                s.justCollided = false;
                        }

                        // shop: disable lateral if purchased
                        if (shop != null && shop.disableLateral) s.lateral = 0.0;

                        // light damping so drift fades slowly
                        s.lateral *= LATERAL_DAMP;

                        // Loss: size-aware lateral threshold + collision capacity
                        double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;
                        // More tolerant lateral threshold (25% larger than before)
                        double lateralThreshold = sizeUnits * pixelsPerUnit * 1.25;

                        if (Math.abs(s.lateral) > lateralThreshold || s.collisions >= s.capacity) {
                                s.currentLink = null;
                                toRemove.add(e);
                                engine.incrementLost();
                                continue;
                        }

                        // arrival clamp (handoff handled elsewhere the same frame)
                        if (s.progress >= 1.0) s.progress = 1.0;

                        // place Transform on the line
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
