package play.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.core.Entity;
import play.components.*;
import play.system.GameEngine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class LevelLoaderV2 {
        private static final ObjectMapper M = new ObjectMapper();

        private LevelLoaderV2() {}

        public static class Loaded {
                public final double totalWire;
                public Loaded(double totalWire){ this.totalWire = totalWire; }
        }

        public static Loaded loadFromResource(GameEngine engine, String path) {
                try (InputStream in = LevelLoaderV2.class.getResourceAsStream(path)) {
                        if (in == null) throw new IllegalArgumentException("Missing level: " + path);
                        JsonNode root = M.readTree(in);

                        double totalWire = root.path("totalWire").asDouble(3000);

                        // map device-id -> system entity
                        Map<String, Entity> deviceMap = new HashMap<>();
                        // map port-id -> port entity
                        Map<String, Entity> portMap = new HashMap<>();

                        // devices (systems)
                        for (JsonNode d : root.path("devices")) {
                                String id = d.path("id").asText();
                                int cap = d.path("capacity").asInt(5);
                                Entity sys = new Entity();
                                sys.add(new Transform(d.path("x").asDouble(0), d.path("y").asDouble(0)));
                                if (d.path("reference").asBoolean(false)) sys.add(new Reference());
                                // you can keep Device component if you want capacity queues per-system
                                deviceMap.put(id, sys);
                                engine.entities().add(sys);
                        }

                        // ports
                        for (JsonNode p : root.path("ports")) {
                                String id  = p.path("id").asText();
                                String dev = p.path("device").asText();
                                String shapeTxt = p.path("shape").asText("square");
                                String ioTxt    = p.path("io").asText("in");
                                boolean producer = p.path("producer").asBoolean(false);
                                double interval = p.path("interval").asDouble(1.0);

                                Entity sys = deviceMap.get(dev);
                                if (sys == null) continue;

                                PortInfo.Shape shape = "triangle".equalsIgnoreCase(shapeTxt)
                                        ? PortInfo.Shape.TRIANGLE : PortInfo.Shape.SQUARE;
                                PortInfo.IO io = "out".equalsIgnoreCase(ioTxt) ? PortInfo.IO.OUT : PortInfo.IO.IN;

                                Entity port = new Entity();
                                port.add(new PortInfo(io, shape, sys));
                                port.add(new Transform(p.path("x").asDouble(), p.path("y").asDouble()));
                                if (io == PortInfo.IO.IN) port.add(new Queue(5));
                                if (producer) sys.add(new Producer(interval));

                                portMap.put(id, port);
                                engine.entities().add(port);
                        }

                        // links
                        for (JsonNode l : root.path("links")) {
                                Entity from = portMap.get(l.path("from").asText());
                                Entity to   = portMap.get(l.path("to").asText());
                                if (from == null || to == null) continue;
                                engine.entities().add(new Entity().add(new Link(from, to)));
                        }

                        return new Loaded(totalWire);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }
}
