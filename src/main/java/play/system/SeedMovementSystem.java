package play.system;

import play.core.Entity;
import play.components.Link;
import play.components.Seed;
import play.components.Transform;
import play.components.PortInfo;

import java.util.List;

/**
 * Moves seeds along their assigned Link entities. When seed arrives it notifies engine (delivered)
 * and if the destination port is marked as reference (PortInfo with a small heuristic) it also increments reachedReference.
 */
public class SeedMovementSystem implements System {
        private final List<Entity> entities;
        private final GameEngine engine;
        private final ShopSystem shop; // consult flags

        public SeedMovementSystem(GameEngine engine, List<Entity> entities, ShopSystem shop) {
                this.entities = entities;
                this.engine = engine;
                this.shop = shop;
        }

        @Override
        public void update(double dt) {
                for (Entity e : List.copyOf(entities)) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);

                        if (s.currentLink == null) continue;
                        Link l = s.currentLink.get(Link.class);
                        if (l == null) continue;

                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);
                        double vx = b.x - a.x, vy = b.y - a.y;
                        double len = Math.hypot(vx, vy);
                        if (len < 1e-6) continue;

                        // compatibility check
                        boolean incompatible = false;
                        if (l.fromPort.has(PortInfo.class)) {
                                PortInfo pi = l.fromPort.get(PortInfo.class);
                                incompatible = (pi.shape == PortInfo.Shape.SQUARE && s.type == Seed.Type.TRIANGLE)
                                        || (pi.shape == PortInfo.Shape.TRIANGLE && s.type == Seed.Type.SQUARE);
                        }

                        double sp = s.speed + (incompatible ? 0.06 : 0.0);
                        s.progress += sp * dt;

                        // lateral disabled by shop
                        if (shop != null && shop.getState().disableLateral) {
                                s.lateral = 0;
                        }

                        double px = a.x + vx * Math.min(1.0, s.progress);
                        double py = a.y + vy * Math.min(1.0, s.progress);
                        double nx = -vy / len, ny = vx / len;
                        px += nx * s.lateral;
                        py += ny * s.lateral;
                        t.x = px; t.y = py;

                        if (s.progress >= 1.0) {
                                // arrival
                                s.currentLink = null;
                                s.progress = 0;
                                s.lateral = 0;

                                // Notify delivered
                                engine.fireDeliveredEvent(s);

                                // If the toPort is a "reference sink" — we detect using a PortInfo convention:
                                if (l.toPort.has(PortInfo.class)) {
                                        PortInfo toInfo = l.toPort.get(PortInfo.class);
                                        // convention: if port is IN and shape==SQUARE and located at x>500 -> treat as reference
                                        // (better: add explicit Reference component in future)
                                        Transform pt = l.toPort.get(Transform.class);
                                        if (toInfo.io == PortInfo.IO.IN && pt != null && pt.x > 500) {
                                                engine.incrementReachedReference();
                                        }
                                }

                                // remove the seed entity visually by marking speed=-1 and letting CollisionSystem or engine sweep it
                                s.speed = -1; // collisions & engine cleanup may remove it from lists
                        }
                }
                // cleanup seeds marked lost or delivered (speed < 0)
                entities.removeIf(en -> en.has(Seed.class) && en.get(Seed.class).speed < 0);
        }
}
