package play.system;

import play.core.Entity;
import play.components.Seed;
import play.components.Transform;

import java.util.List;
import java.util.Random;

/**
 * Simple producer: periodically spawns a Seed attached to a Link (if present).
 * Uses engine.incrementProduced() so the engine knows total produced packets.
 */
public class ProductionSystem implements System {
        private final List<Entity> allEntities;
        private final GameEngine engine;
        private double timer = 0.0;
        private final double interval;

        public ProductionSystem(GameEngine engine, List<Entity> allEntities, double intervalSeconds) {
                this.engine = engine;
                this.allEntities = allEntities;
                this.interval = intervalSeconds;
        }

        @Override
        public void update(double dt) {
                timer += dt;
                if (timer < interval) return;
                timer = 0;

                // find an OUT port to spawn from (heuristic: Transform.x < mid)
                Entity spawnFrom = null;
                for (Entity e : allEntities) {
                        if (e.has(play.components.PortInfo.class) && e.has(Transform.class)) {
                                play.components.PortInfo p = e.get(play.components.PortInfo.class);
                                Transform t = e.get(Transform.class);
                                if (p.io == play.components.PortInfo.IO.OUT && t.x < 200) {
                                        spawnFrom = e;
                                        break;
                                }
                        }
                }
                if (spawnFrom == null) return;

                // find a link entity which uses this port as fromPort
                Entity link = null;
                for (Entity e : allEntities) {
                        if (e.has(play.components.Link.class)) {
                                play.components.Link l = e.get(play.components.Link.class);
                                if (l.fromPort.equals(spawnFrom)) {
                                        link = e;
                                        break;
                                }
                        }
                }
                if (link == null) return;

                // spawn seed
                Entity seed = new Entity();
                seed.add(new Transform(spawnFrom.get(Transform.class).x, spawnFrom.get(Transform.class).y));
                // randomize type a little
                Random r = new Random();
                Seed.Type t = r.nextBoolean() ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;
                Seed s = new Seed(t);
                s.currentLink = link;
                s.progress = 0.0;
                seed.add(s);
                allEntities.add(seed);

                engine.incrementProduced();
        }
}
