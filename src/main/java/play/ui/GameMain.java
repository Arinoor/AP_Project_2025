package play.ui;

import play.core.Entity;
import play.components.*;
import play.level.LevelLoader;
import play.system.*;

import java.lang.System;

public class GameMain {
        public static void main(String[] args) {
                GameEngine engine = new GameEngine();

                // If you have a JSON level in resources, load it:
                // LevelLoader.loadFromResource(engine, "/levels/level1.json");

                // Otherwise build a tiny sample level:
                buildSampleLevel(engine);

                ShopSystem shop = new ShopSystem(engine);
                ProductionSystem prod = new ProductionSystem(engine, engine.entities(), 0.2);
                QueueSystem queue = new QueueSystem(engine, engine.entities());
                RoutingSystem route = new RoutingSystem(engine, engine.entities());
                SeedMovementSystem move = new SeedMovementSystem(engine, engine.entities(), shop);
                CollisionSystem collide = new CollisionSystem(engine, engine.entities(), shop);

                engine.addSystem(shop);
                engine.addSystem(prod);
                engine.addSystem(queue);
                engine.addSystem(move);
                engine.addSystem(collide);
                engine.addSystem(route);

                // simple fixed-step loop for demo
                double dt = 1.0 / 60.0;
                for (int i = 0; i < 60 * 30; i++) { // ~30s
                        engine.tick(dt);
                }

                System.out.printf("Produced: %d, ReachedRef: %d, Lost: %d, Coins: %d%n",
                        engine.getProducedCount(), engine.getReachedReference(), engine.getLostCount(), engine.getCoins());
        }

        private static void buildSampleLevel(GameEngine engine) {
                // two systems + 1 reference sink, 2 out ports, queues on inputs, one link chain

                // System A (producer)
                Entity sysA = new Entity().add(new Transform(100, 200)).add(new Producer(0.8));
                engine.entities().add(sysA);

                Entity aOutSquare = new Entity()
                        .add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.SQUARE, sysA))
                        .add(new Transform(150, 200));
                engine.entities().add(aOutSquare);

                // System B (mid, IN + OUT)
                Entity sysB = new Entity().add(new Transform(350, 200));
                engine.entities().add(sysB);

                Entity bInSquare = new Entity()
                        .add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.SQUARE, sysB))
                        .add(new Transform(320, 200))
                        .add(new Queue(5));
                engine.entities().add(bInSquare);

                Entity bOutTri = new Entity()
                        .add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.TRIANGLE, sysB))
                        .add(new Transform(380, 200));
                engine.entities().add(bOutTri);

                // System C (reference sink)
                Entity sysC = new Entity().add(new Transform(600, 200)).add(new Reference());
                engine.entities().add(sysC);

                Entity cInTri = new Entity()
                        .add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.TRIANGLE, sysC))
                        .add(new Transform(570, 200))
                        .add(new Queue(5));
                engine.entities().add(cInTri);

                // Links: A.outSquare -> B.inSquare ; B.outTri -> C.inTri
                engine.entities().add(new Entity().add(new Link(aOutSquare, bInSquare)));
                engine.entities().add(new Entity().add(new Link(bOutTri, cInTri)));
        }
}
