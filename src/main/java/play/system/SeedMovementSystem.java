package play.system;

import play.components.Link;
import play.components.Reference;
import play.components.Seed;
import play.components.Transform;
import play.core.Entity;

import java.util.ArrayList;
import java.util.List;

public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem shop;
        private final RoutingSystem router;

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shop) {
                this.engine = engine;
                this.entities = entities;
                this.shop = shop;
                this.router = new RoutingSystem(entities);
        }

        @Override
        public void update(double dt) {
                List<Entity> toRemove = new ArrayList<>();

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
                                // arrived
                                s.currentLink = null;
                                engine.notifySeedDelivered(s);

                                // Reference sink reached?
                                if (l.toPort.has(Reference.class)) {
                                        engine.incrementReachedReference();
                                        toRemove.add(e); // remove the seed; it's done
                                        continue;
                                }

                                // Otherwise, auto-route to next link if possible
                                boolean routed = router.route(s, l.toPort);
                                if (routed) {
                                        // snap to starting port of chosen link
                                        Link next = s.currentLink.get(Link.class);
                                        Transform fromT = next.fromPort.get(Transform.class);
                                        t.x = fromT.x;
                                        t.y = fromT.y;
                                        continue;
                                } else {
                                        // No outgoing link; seed rests at the port (idle).
                                        // (Optional: you can despawn instead)
                                        continue;
                                }
                        }

                        // move toward target
                        double step = s.speed * dt;
                        if (step > 0 && dist > 0) {
                                t.x += dx / dist * step;
                                t.y += dy / dist * step;
                        }
                }

                // clean up any completed seeds
                if (!toRemove.isEmpty()) entities.removeAll(toRemove);
        }
}
