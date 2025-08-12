package play.model.systems;

import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;

import java.util.ArrayList;
import java.util.List;

import static play.model.constants.GameBalance.IMPACT_COOLDOWN_OFF;
import static play.model.constants.GameBalance.LATERAL_DAMP;

/**
 * Moves seeds along links, applies accel, and removes seeds that exceed
 * lateral threshold, collision capacity, or NOISE > sizeUnits().
 */
public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem.ShopState shop;

        private final double pixelsPerUnit;

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

                        // accel -> speed, then distance
                        s.speed += s.accel * dt;
                        if (s.speed < 0) s.speed = 0;
                        double dist = s.speed * dt + 0.5 * s.accel * dt * dt;
                        s.progress += dist / length;

                        // decay impact energy (collision guard)
                        s.impactEnergy *= Math.pow(0.6, dt * 60.0);
                        if (s.impactEnergy < IMPACT_COOLDOWN_OFF) s.justCollided = false;

                        if (shop != null && shop.disableLateral) s.lateral = 0.0;
                        s.lateral *= LATERAL_DAMP;

                        // thresholds
                        double lateralThreshold = s.sizeUnits() * pixelsPerUnit;

                        // LOSS if any condition triggered
                        if (Math.abs(s.lateral) > lateralThreshold
                                || s.collisions >= s.capacity
                                || s.noise > s.sizeUnits()) {
                                s.currentLink = null;
                                toRemove.add(e);
                                engine.incrementLost();
                                continue;
                        }

                        if (s.progress >= 1.0) s.progress = 1.0;

                        // place on line
                        Transform st = e.get(Transform.class);
                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);
                        st.x = a.x + (b.x - a.x) * s.progress;
                        st.y = a.y + (b.y - a.y) * s.progress;
                }

                for (Entity e : toRemove) entities.remove(e);
        }
}
