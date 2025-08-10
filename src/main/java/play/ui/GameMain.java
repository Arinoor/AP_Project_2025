package play.ui;

import java.util.List;

import play.core.Entity;
import play.system.GameEngine;
import play.system.ShopSystem;
import play.system.ProductionSystem;
import play.system.SeedMovementSystem;
import play.system.CollisionSystem;
import play.components.Seed;

/**
 * Wires systems into the engine using the engine's Consumer<Double>-style scheduler.
 */
public class GameMain {

        public static void main(String[] args) {
                GameEngine engine = new GameEngine();

                // Systems
                ShopSystem shopSystem = new ShopSystem(engine);
                ProductionSystem prodSys = new ProductionSystem(engine, engine.entities());
                SeedMovementSystem moveSys = new SeedMovementSystem(engine, engine.entities(), shopSystem);
                CollisionSystem colSys = new CollisionSystem(engine, engine.entities(), shopSystem);

                // Wrap order: collisions -> movement -> production -> shop timers
                engine.addSystem(dt -> {
                        if (!shopSystem.getState().disableCollisions) {
                                colSys.update(dt);
                        }

                        if (shopSystem.getState().disableLateral) {
                                List<Entity> es = engine.entities();
                                for (Entity e : es) {
                                        if (e.has(Seed.class)) e.get(Seed.class).lateral = 0.0;
                                }
                        }

                        moveSys.update(dt);
                        prodSys.update(dt);
                        shopSystem.update(dt);
                });

                // Kick off
                engine.run();
        }
}
