package play.system;

import java.util.List;

import play.core.Entity;
import play.components.Seed;
import play.components.Link;
import play.components.Reference;

/**
 * Moves seeds along their current link.
 * - Applies forward accel (triangles on incompatible starts, set by ProductionSystem).
 * - Arrivals: detach & notify engine. Reference sinks bump reached counter.
 *   (Coins should be awarded by DeliveryListeners wired to notifySeedDelivered.)
 */
public class SeedMovementSystem {

        private final GameEngine engine;
        private final List<Entity> world;
        @SuppressWarnings("unused")
        private final ShopSystem shop; // reserved if we add movement-affecting toggles

        public SeedMovementSystem(GameEngine engine, List<Entity> world, ShopSystem shop) {
                this.engine = engine;
                this.world = world;
                this.shop = shop;
        }

        public void update(double dt) {
                for (Entity e : world) {
                        if (!e.has(Seed.class)) continue;

                        Seed s = e.get(Seed.class);
                        if (!s.alive || s.currentLink == null) continue;

                        // forward accel (set in ProductionSystem based on compatibility)
                        if (s.accel != 0.0) {
                                s.speed += s.accel * dt;
                        }
                        double v = Math.max(0.0, s.speed);

                        Link l = s.currentLink;
                        if (l.length <= 1e-6) {
                                // degenerate link: deliver immediately to avoid NaN
                                onArrive(s, l);
                                continue;
                        }

                        // Advance param by distance/length
                        double dp = (v / l.length) * dt;
                        s.progress += dp;

                        if (s.progress >= 1.0) {
                                onArrive(s, l);
                        }
                }
        }

        private void onArrive(Seed s, Link l) {
                // Detach from link
                s.currentLink = null;
                s.progress = 0.0;

                // Notify delivery (coins via listeners)
                engine.notifySeedDelivered(s);

                // Reference sink reached?
                if (l.toPort != null && l.toPort.has(Reference.class)) {
                        engine.incrementReachedReference();
                }

                // Further routing (auto-hop) is handled by a dedicated routing system or production step.
                // We keep movement single-responsibility here.
        }
}
