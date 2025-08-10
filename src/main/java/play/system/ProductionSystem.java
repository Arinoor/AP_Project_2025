package play.system;

import play.components.*;
import play.core.Entity;

import java.util.List;

public class ProductionSystem implements System {
        private final GameEngine engine;
        private final List<Entity> world;
        private final RoutingSystem router;

        public ProductionSystem(GameEngine engine, List<Entity> world) {
                this.engine = engine;
                this.world = world;
                this.router = new RoutingSystem(world);
        }

        @Override
        public void update(double dt) {
                for (Entity port : world) {
                        if (!port.has(Producer.class) || !port.has(PortInfo.class) || !port.has(Transform.class)) continue;
                        Producer prod = port.get(Producer.class);
                        prod.timer += dt;
                        if (prod.timer < prod.intervalSec) continue;
                        prod.timer = 0.0;

                        // choose seed type from this output port's shape
                        PortInfo portInfo = port.get(PortInfo.class);
                        Seed.Type type = (portInfo.shape == PortInfo.Shape.SQUARE) ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;

                        // Create seed entity at port position
                        Entity seed = new Entity();
                        Transform pt = port.get(Transform.class);
                        seed.add(new Transform(pt.x, pt.y));
                        Seed s = new Seed(type);
                        seed.add(s);
                        world.add(seed);

                        // Immediately route to an outgoing link of this port (if any)
                        boolean routed = router.route(s, port);
                        if (!routed) {
                                // no outgoing link; seed will sit here
                        } else {
                                engine.incrementProduced();
                        }
                }
        }
}
