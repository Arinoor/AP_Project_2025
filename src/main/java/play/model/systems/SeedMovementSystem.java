package play.model.systems;

import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;
import play.utils.WiringUtils;

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
                List<Entity> toRemove = new ArrayList<>();

                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
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
                        WiringUtils.Pt pos = WiringUtils.pointAlongNormalized(l, s.progress);
                        Transform st = e.get(Transform.class);
                        st.x = pos.x;
                        st.y = pos.y;

                        // TODO: if you have handoff logic on reaching end of link, keep it here when s.progress == 1.0
                }

                for (Entity e : toRemove) entities.remove(e);
        }
}
