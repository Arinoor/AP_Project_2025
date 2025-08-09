package play.system;

import play.components.Link;
import play.components.Reference;
import play.components.Seed;
import play.components.Transform;
import play.core.Entity;

import java.util.List;

/**
 * Moves seeds along their currentLink entity toward the Link.toPort transform.
 * When a seed reaches its destination:
 *  - notify engine about a delivered seed (notifySeedDelivered / fireDeliveredEvent)
 *  - if the destination port entity has Reference component -> incrementReachedReference()
 */
public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem shop;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shop) {
                this.engine = engine;
                this.entities = entities;
                this.shop = shop;
        }

        @Override
        public void update(double dt) {
                for (Entity e : List.copyOf(entities)) { // iterate a snapshot to allow modifications
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);

                        if (s.currentLink == null) continue;

                        Link l = s.currentLink.get(Link.class);
                        if (l == null || l.toPort == null) continue;

                        Transform toT = l.toPort.get(Transform.class);
                        if (toT == null) continue;

                        double dx = toT.x - t.x;
                        double dy = toT.y - t.y;
                        double dist = Math.hypot(dx, dy);

                        // arrival threshold: if close enough, consider delivered
                        if (dist < 1.0) {
                                // detach from link
                                s.currentLink = null;

                                // notify engine / listeners
                                engine.notifySeedDelivered(s);

                                // increment reached reference if destination is a reference sink
                                if (l.toPort.has(Reference.class)) {
                                        engine.incrementReachedReference();
                                }

                                // Optionally the seed entity may be removed / marked done. We'll leave removal to higher-level systems (collision/system)
                                continue;
                        }

                        // speed applies per-second; note shop effects already applied via ShopSystem wrapper in orchestrator
                        double step = s.speed * dt;
                        if (step <= 0) continue;
                        t.x += dx / dist * step;
                        t.y += dy / dist * step;
                }
        }
}
