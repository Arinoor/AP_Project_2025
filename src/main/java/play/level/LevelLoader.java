package play.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.core.Entity;
import play.system.GameEngine;
import play.components.*;

import java.io.IOException;
import java.io.InputStream;

public final class LevelLoader {
        private static final ObjectMapper M = new ObjectMapper();

        private LevelLoader() {}

        public static void loadFromResource(GameEngine engine, String path) {
                try (InputStream in = LevelLoader.class.getResourceAsStream(path)) {
                        if (in == null) {
                                throw new IllegalArgumentException("Missing level: " + path);
                        }
                        JsonNode root = M.readTree(in);

                        // systems
                        for (JsonNode sys : root.path("systems")) {
                                Entity sysE = new Entity();
                                engine.entities().add(sysE);

                                Transform t = new Transform(sys.path("x").asDouble(0), sys.path("y").asDouble(0));
                                sysE.add(t);

                                if (sys.path("reference").asBoolean(false)) sysE.add(new Reference());
                                if (sys.has("producer")) {
                                        double interval = sys.get("producer").path("interval").asDouble(1.0);
                                        sysE.add(new Producer(interval));
                                }

                                for (JsonNode p : sys.path("ports")) {
                                        Entity portE = new Entity();
                                        engine.entities().add(portE);

                                        PortInfo.IO io = "IN".equalsIgnoreCase(p.path("io").asText("IN")) ? PortInfo.IO.IN : PortInfo.IO.OUT;
                                        PortInfo.Shape shape = "SQUARE".equalsIgnoreCase(p.path("shape").asText("SQUARE"))
                                                ? PortInfo.Shape.SQUARE : PortInfo.Shape.TRIANGLE;

                                        portE.add(new PortInfo(io, shape, sysE));
                                        portE.add(new Transform(
                                                t.x + p.path("dx").asDouble(0),
                                                t.y + p.path("dy").asDouble(0)
                                        ));
                                        if (io == PortInfo.IO.IN) portE.add(new Queue(5));
                                }
                        }

                        // links
                        for (JsonNode l : root.path("links")) {
                                int fromIdx = l.path("from").asInt();
                                int toIdx   = l.path("to").asInt();
                                Entity fromPort = nthPort(engine, fromIdx);
                                Entity toPort   = nthPort(engine, toIdx);
                                if (fromPort == null || toPort == null) continue;

                                Entity linkE = new Entity();
                                engine.entities().add(linkE);
                                linkE.add(new Link(fromPort, toPort));
                        }

                } catch (IOException e) {
                        throw new RuntimeException(e);
                }
        }

        private static Entity nthPort(GameEngine engine, int index) {
                int i = 0;
                for (Entity e : engine.entities()) {
                        if (e.has(PortInfo.class)) {
                                if (i == index) return e;
                                i++;
                        }
                }
                return null;
        }
}
