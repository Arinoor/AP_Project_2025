package play.model.systems;

import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;
import play.utils.WiringUtils;
import play.model.components.Disabled;
import play.model.constants.GameBalance;


import java.util.ArrayList;
import java.util.List;

import static play.model.constants.GameBalance.IMPACT_COOLDOWN_OFF;
import static play.model.constants.GameBalance.LATERAL_DAMP;

/**
 * Moves seeds along links (polyline-aware), applies acceleration,
 * and removes seeds that exceed thresholds (lateral, collisions, noise).
 *
 * Backward compatible with previous straight-line logic: with zero bends,
 * the path is identical to the old segment.
 */
public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        /** Converts "port units" (seed sizeUnits) to pixels for lateral threshold. */
        private final double pixelsPerUnit;

        private static final double MIN_LINK_LEN = 1e-6;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shopSystem, double pixelsPerUnit) {
                this.engine = engine;
                this.entities = entities;
                this.shop = (shopSystem != null) ? shopSystem.getState() : null;
                this.pixelsPerUnit = Math.max(6.0, pixelsPerUnit);
        }

        @Override
        public void update(double dt) {

                for (Entity sys : entities) {
                        if (sys.has(Disabled.class)) {
                                Disabled d = sys.get(Disabled.class);
                                d.remaining -= dt;
                                if (d.remaining <= 0) sys.remove(Disabled.class);
                        }
                }

                List<Entity> toRemove = new ArrayList<>();

                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);

                        if (s.type == Seed.Type.SECURE_PROTECTED) {
                                handleSecureProtectedMovement(s, dt);
                        }

                        if (s.currentLink == null) continue;

                        Link l = s.currentLink;

                        // Polyline length (up-to-date with bends)
                        double length = Math.max(MIN_LINK_LEN, WiringUtils.pathLength(l));

                        // Integrate velocity and distance
                        s.speed += s.accel * dt;
                        if (s.speed < 0) s.speed = 0;
                        double ds = s.speed * dt + 0.5 * s.accel * dt * dt;

                        // Convert to normalized progress on [0..1] via arc-length
                        s.progress += ds / length;
                        if (s.progress >= 1.0) s.progress = 1.0;

                        // Impact cooldown decay
                        s.impactEnergy *= Math.pow(0.6, dt * 60.0);
                        if (s.impactEnergy < IMPACT_COOLDOWN_OFF) s.justCollided = false;

                        // Lateral damping (or disabled by shop)
                        if (shop != null && shop.disableLateral) s.lateral = 0.0;
                        s.lateral *= LATERAL_DAMP;

                        // Loss thresholds
                        double lateralThreshold = s.sizeUnits() * pixelsPerUnit;
                        if (Math.abs(s.lateral) > lateralThreshold
                                || s.collisions >= s.capacity
                                || s.noise > s.sizeUnits()) {
                                s.currentLink = null;
                                toRemove.add(e);
                                engine.incrementLost();
                                continue;
                        }

                        // Place on polyline
                        WiringUtils.Pt pos = s.returning
                                ? WiringUtils.pointAlongNormalized(l, 1.0 - s.progress)
                                : WiringUtils.pointAlongNormalized(l, s.progress);
                        Transform st = e.get(Transform.class);
                        st.x = pos.x;
                        st.y = pos.y;

                        // TODO: if you have handoff logic on reaching end of link, keep it here when s.progress == 1.0
                }

                for (Entity e : toRemove) entities.remove(e);
        }

        private void handleSecureProtectedMovement(Seed s, double dt) {
                final double DESIRED_DISTANCE = 300.0; // pixels
                final double BASE_SPEED = 120.0; // same as secure packets

                // Find all other packets on the same link
                List<Seed> otherPackets = new ArrayList<>();
                for (Entity e : entities) {
                        if (e.has(Seed.class)) {
                                Seed other = e.get(Seed.class);
                                if (other != s && other.currentLink == s.currentLink) {
                                        otherPackets.add(other);
                                }
                        }
                }

                // Calculate movement adjustment based on distances
                double adjustment = 0;
                for (Seed other : otherPackets) {
                        double distance = Math.abs(s.arcPos - other.arcPos);
                        if (distance < DESIRED_DISTANCE) {
                                // Move away from other packets
                                double direction = Math.signum(s.arcPos - other.arcPos);
                                adjustment += direction * (DESIRED_DISTANCE - distance) / DESIRED_DISTANCE;
                        }
                }

                // Apply adjustment to speed (both positive and negative for forward/backward movement)
                s.speed = BASE_SPEED + (adjustment * BASE_SPEED);

                // Ensure we don't move too fast in either direction
                s.speed = Math.max(-BASE_SPEED * 2, Math.min(BASE_SPEED * 2, s.speed));
        }
}

