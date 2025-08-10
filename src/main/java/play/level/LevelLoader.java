package play.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.core.Entity;
import play.system.GameEngine;
import play.components.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Loads a level from JSON and populates entities.
 * NOTE: Removed calls to GameEngine#setTotalWire and #createEntity to avoid your current errors.
 * We push new entities via `engine.entities().add(new Entity())`.
 */
public final class LevelLoader {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        public static void loadFromResource(GameEngine engine, String resourcePath) {
                try (InputStream in = LevelLoader.class.getResourceAsStream(resourcePath)) {
                        if (in == null) throw new IllegalArgumentException("Missing resource: " + resourcePath);
                        JsonNode root = MAPPER.readTree(in);

                        // wire budget (if you want to use it, expose a setter in GameEngine and call it there)
                        // if (root.has("totalWire")) engine.setTotalWire(root.get("totalWire").asDouble());

                        // systems
                        if (root.has("systems")) {
                                for (JsonNode sysNode : root.get("systems")) {
                                        Entity sysE = new Entity();
                                        engine.entities().add(sysE);

                                        // Transform
                                        Transform t = new Transform(
                                                sysNode.path("x").asDouble(0),
                                                sysNode.path("y").asDouble(0));
                                        sysE.add(t);

                                        // Indicator/Reference/Producer flags
                                        if (sysNode.path("reference").asBoolean(false)) {
                                                sysE.add(new Reference());
                                        }
                                        if (sysNode.has("producer")) {
                                                double interval = sysNode.get("producer").path("interval").asDouble(1.0);
                                                sysE.add(new Producer(interval));
                                        }

                                        // Ports
                                        if (sysNode.has("ports")) {
                                                for (JsonNode p : sysNode.get("ports")) {
                                                        Entity portE = new Entity();
                                                        engine.entities().add(portE);

                                                        PortInfo.IO io = "IN".equalsIgnoreCase(p.path("io").asText("IN"))
                                                                ? PortInfo.IO.IN : PortInfo.IO.OUT;
                                                        PortInfo.Shape shape = "SQUARE".equalsIgnoreCase(p.path("shape").asText("SQUARE"))
                                                                ? PortInfo.Shape.SQUARE : PortInfo.Shape.TRIANGLE;

                                                        PortInfo pinfo = new PortInfo(io, shape, sysE);
                                                        portE.add(pinfo);

                                                        Transform pt = new Transform(
                                                                sysNode.path("x").asDouble(0) + p.path("dx").asDouble(0),
                                                                sysNode.path("y").asDouble(0) + p.path("dy").asDouble(0));
                                                        portE.add(pt);

                                                        // Every input port gets a queue with capacity 5 per doc
                                                        if (io == PortInfo.IO.IN) {
                                                                portE.add(new Queue(5));
                                                        }
                                                }
                                        }
                                }
                        }

                        // links
                        if (root.has("links")) {
                                for (JsonNode l : root.get("links")) {
                                        int fromIndex = l.path("from").asInt();
                                        int toIndex   = l.path("to").asInt();
                                        Entity fromPort = nthPort(engine, fromIndex);
                                        Entity toPort   = nthPort(engine, toIndex);
                                        if (fromPort == null || toPort == null) continue;

                                        Entity linkE = new Entity();
                                        engine.entities().add(linkE);
                                        linkE.add(new Link(fromPort, toPort));
                                }
                        }

                        // initial seeds (optional)
                        if (root.has("seeds")) {
                                for (JsonNode s : root.get("seeds")) {
                                        Entity seedE = new Entity();
                                        engine.entities().add(seedE);

                                        Seed.Type type = "SQUARE".equalsIgnoreCase(s.path("type").asText("SQUARE"))
                                                ? Seed.Type.SQUARE : Seed.Type.TRIANGLE;
                                        Seed seed = new Seed(type);
                                        seedE.add(seed);

                                        Transform st = new Transform(s.path("x").asDouble(0), s.path("y").asDouble(0));
                                        seedE.add(st);
                                }
                        }
                } catch (IOException e) {
                        throw new RuntimeException("Failed to load: " + resourcePath, e);
                }
        }

        // helper: get Nth entity that has PortInfo
        private static Entity nthPort(GameEngine engine, int idx) {
                int i = 0;
                for (Entity e : engine.entities()) {
                        if (e.has(PortInfo.class)) {
                                if (i == idx) return e;
                                i++;
                        }
                }
                return null;
        }
}
