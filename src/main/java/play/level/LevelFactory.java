package play.level;

import play.core.Entity;
import play.core.Entity;
import play.system.GameEngine;
import play.components.*;

import java.util.List;

public class LevelFactory {
        public static void buildDemo(GameEngine engine){
                // create 3 conduits (each conduit is an entity for simplicity)
                Entity source = engine.createEntity(); source.add(new Transform(80, 120));
                Entity mid = engine.createEntity(); mid.add(new Transform(320, 120));
                Entity sink = engine.createEntity(); sink.add(new Transform(560, 120));
                // ports are small entities attached to conduits
                Entity outA = engine.createEntity(); outA.add(new Transform(120,140)); outA.add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.SQUARE));
                Entity inB = engine.createEntity(); inB.add(new Transform(300,140)); inB.add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.SQUARE));
                Entity outB = engine.createEntity(); outB.add(new Transform(340,140)); outB.add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.TRIANGLE));
                Entity inC = engine.createEntity(); inC.add(new Transform(540,140)); inC.add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.TRIANGLE));
                // link from A->B and B->C
                Entity link1 = engine.createEntity(); link1.add(new Link(outA, inB, 200));
                Entity link2 = engine.createEntity(); link2.add(new Link(outB, inC, 220));
                // a seed prefab: attach Seed + Transform and put the seed on link1
                Entity seed = engine.createEntity();
                seed.add(new Transform(120,140));
                seed.add(new Seed(Seed.Type.SQUARE));
                seed.get(Seed.class).currentLink = link1;
                seed.get(Seed.class).progress = 0;
                // add to engine entities is already done by createEntity()
        }
}
