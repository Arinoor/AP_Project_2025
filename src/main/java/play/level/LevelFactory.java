package play.level;

import play.core.Entity;
import play.system.GameEngine;
import play.components.*;

import java.util.List;

/**
 * Builds a simple demo level that matches the spec elements: ports, links, producers
 * and one sink. You can replace with a JSON file loader later (I included a sample JSON resource).
 */
public final class LevelFactory {
        public static void buildDemo(GameEngine engine){
                // Conduits: visual anchors (not necessary but helpful)
                Entity conduitA = engine.createEntity(); conduitA.add(new Transform(80, 120));
                Entity conduitB = engine.createEntity(); conduitB.add(new Transform(320, 120));
                Entity conduitC = engine.createEntity(); conduitC.add(new Transform(560, 120));

                // Ports: output of A, input B; output B, input C
                Entity outA = engine.createEntity(); outA.add(new Transform(120,140));
                outA.add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.SQUARE));

                Entity inB = engine.createEntity(); inB.add(new Transform(300,140));
                inB.add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.SQUARE));

                Entity outB = engine.createEntity(); outB.add(new Transform(340,140));
                outB.add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.TRIANGLE));

                Entity inC = engine.createEntity(); inC.add(new Transform(540,140));
                inC.add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.TRIANGLE));

                // Links
                Entity link1 = engine.createEntity(); link1.add(new Link(outA, inB, 220));
                Entity link2 = engine.createEntity(); link2.add(new Link(outB, inC, 220));

                // Seed initial set on link1 (two seeds)
                Entity s1 = engine.createEntity(); s1.add(new Transform(120,140));
                Seed sd1 = new Seed(Seed.Type.SQUARE); sd1.currentLink = link1; sd1.progress = 0.05; s1.add(sd1);

                Entity s2 = engine.createEntity(); s2.add(new Transform(120,150));
                Seed sd2 = new Seed(Seed.Type.TRIANGLE); sd2.currentLink = link1; sd2.progress = 0.0; s2.add(sd2);

                // Done: engine will now own all entities added via createEntity()
        }
}
