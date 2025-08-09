package play.system;

import play.components.Link;
import play.components.Reference;
import play.components.Seed;
import play.components.Transform;
import play.core.Entity;

import java.util.List;

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
                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);

                        if (s.currentLink == null) continue;

                        Link l = s.currentLink.get(Link.class);
                        Transform toT = l.toPort.get(Transform.class);

                        double dx = toT.x - t.x;
                        double dy = toT.y - t.y;
                        double dist = Math.hypot(dx, dy);

                        if (dist < 1.0) {
                                s.currentLink = null;
                                engine.fireDeliveredEvent(s);

                                if (l.toPort.has(Reference.class)) {
                                        engine.incrementReachedReference();
                                }
                        } else {
                                double speed = s.speed * dt;
                                t.x += dx / dist * speed;
                                t.y += dy / dist * speed;
                        }
                }
        }
}
