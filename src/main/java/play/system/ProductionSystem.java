package play.system;

import play.components.*;
import play.core.Entity;

import java.util.Objects;

public class ProductionSystem implements System {
        private final GameEngine engine;
        private final java.util.List<Entity> world;

        public ProductionSystem(GameEngine engine, java.util.List<Entity> world) {
                this.engine = engine;
                this.world = world;
        }

        @Override
        public void update(double dt) {
                for (Entity port : world) {
                        if (!port.has(Producer.class) || !port.has(PortInfo.class) || !port.has(Transform.class)) continue;
                        Producer prod = port.get(Producer.class);
                        prod.timer += dt;
                        if (prod.timer < prod.intervalSec) continue;

                        // find a free link from this port
                        Entity freeLink = null;
                        for (Entity e : world) {
                                if (!e.has(Link.class)) continue;
                                Link L = e.get(Link.class);
                                if (!Objects.equals(L.fromPort, port)) continue;
                                if (isFree(e)) { freeLink = e; break; }
                        }
                        if (freeLink == null) continue; // block; do not reset timer

                        // spawn a seed
                        PortInfo pinfo = port.get(PortInfo.class);
                        Seed.Type type = (pinfo.shape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                        Entity seed = engine.createEntity();
                        Transform pt = port.get(Transform.class);
                        seed.add(new Transform(pt.x, pt.y));

                        Seed s = new Seed(type);
                        boolean compatibleStart = (type == Seed.Type.SQUARE && pinfo.shape == PortInfo.Shape.SQUARE)
                                || (type == Seed.Type.TRIANGLE && pinfo.shape == PortInfo.Shape.TRIANGLE);
                        if (type == Seed.Type.SQUARE) {
                                double base = 120.0;
                                s.speed = compatibleStart ? base * 0.5 : base;
                                s.accel = 0.0;
                        } else {
                                s.speed = 140.0;
                                s.accel = compatibleStart ? 0.0 : 220.0;
                        }
                        seed.add(s);

                        s.currentLink = freeLink;
                        s.progress = 0.0;

                        engine.incrementProduced();
                        prod.timer = 0.0; // consume one interval
                }
        }

        private boolean isFree(Entity link) {
                for (Entity e : world) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false;
                }
                return true;
        }
}
