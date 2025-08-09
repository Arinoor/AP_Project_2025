package play.system;

import play.core.Entity;
import play.components.Link;
import play.components.Seed;
import play.components.Transform;
import play.components.PortInfo;

import java.util.List;

/**
 * Moves seeds along link. If the shop picked "disable lateral", MovementSystem zeroes lateral.
 * This version fixes the stray 'InfoHolder' error.
 */
public class SeedMovementSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;
        private final ShopSystem shop; // nullable, for checking shop flags

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shop){
                this.engine = engine;
                this.entities = entities;
                this.shop = shop;
        }


        @Override
        public void update(double dt) {
                // iterate over a copy to avoid concurrent modification problems
                for (Entity e : List.copyOf(entities)) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;

                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);

                        if (s.currentLink == null) continue;
                        Link link = s.currentLink.get(Link.class);
                        if (link == null) continue;

                        Transform a = link.fromPort.get(Transform.class);
                        Transform b = link.toPort.get(Transform.class);
                        double vx = b.x - a.x, vy = b.y - a.y;
                        double len = Math.hypot(vx, vy);
                        if (len < 1e-6) continue;

                        // compatibility check using the fromPort's PortInfo
                        boolean incompatible = false;
                        if (link.fromPort.has(PortInfo.class)) {
                                PortInfo pi = link.fromPort.get(PortInfo.class);
                                incompatible = (pi.shape == PortInfo.Shape.SQUARE && s.type == Seed.Type.TRIANGLE)
                                        || (pi.shape == PortInfo.Shape.TRIANGLE && s.type == Seed.Type.SQUARE);
                        }

                        double sp = s.speed + (incompatible ? 0.06 : 0.0);
                        s.progress += sp * dt;

                        double px = a.x + vx * Math.min(1.0, s.progress);
                        double py = a.y + vy * Math.min(1.0, s.progress);

                        double nx = -vy / len, ny = vx / len;

                        // If Shop disables lateral drift, reset lateral component
                        if (shop != null && shop.getState().disableLateral) {
                                s.lateral = 0;
                        }

                        px += nx * s.lateral;
                        py += ny * s.lateral;

                        t.x = px;
                        t.y = py;

                        if (s.progress >= 1.0) {
                                // mark arrived and fire event
                                s.currentLink = null;
                                s.progress = 0;
                                s.lateral = 0;

                                // notify engine: this will be listened by UI/controller
                                engine.fireDeliveredEvent(s);
                        }
                }
        }
}
