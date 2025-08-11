package play.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.core.Entity;
import play.components.*;
import play.system.GameEngine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * LevelLoaderV2
 *
 * Loads ONLY systems and ports from JSON. Does NOT create links.
 * Player creates links at runtime.
 *
 * JSON format (links, if present, are ignored):
 * {
 *   "totalWire": 5000,
 *   "devices": [
 *     {
 *       "id": "SRC-1", "x": 150, "y": 240,
 *       "producer": { "interval": 0.6, "countSquare": 10, "countTriangle": 8 }
 *     },
 *     { "id": "REF-1", "x": 700, "y": 280, "reference": true }
 *   ],
 *   "ports": [
 *     { "id": "S1:outS", "device": "SRC-1", "x": 200, "y": 240, "shape": "square",   "io": "out" },
 *     { "id": "S1:outT", "device": "SRC-1", "x": 200, "y": 280, "shape": "triangle", "io": "out" },
 *     { "id": "R1:inS",  "device": "REF-1", "x": 670, "y": 260, "shape": "square",   "io": "in"  }
 *   ]
 * }
 */
public final class LevelLoaderV2 {

        public static final class Loaded {
                public double totalWire = 3000;
                public final Map<String, Entity> systemsById = new HashMap<>();
                public final Map<String, Entity> portsById   = new HashMap<>();
        }

        private static final ObjectMapper M = new ObjectMapper();

        private LevelLoaderV2() {}

        public static Loaded loadFromResource(GameEngine engine, String path) {
                Loaded out = new Loaded();
                try (InputStream in = LevelLoaderV2.class.getResourceAsStream(path)) {
                        if (in == null) {
                                throw new IllegalArgumentException("Missing level resource: " + path);
                        }
                        JsonNode root = M.readTree(in);

                        out.totalWire = root.path("totalWire").asDouble(3000.0);

                        // 1) Devices (systems)
                        for (JsonNode d : root.path("devices")) {
                                String id = d.path("id").asText();
                                double x  = d.path("x").asDouble(0);
                                double y  = d.path("y").asDouble(0);

                                Entity systemE = new Entity();
                                systemE.add(new Transform(x, y));

                                // Reference?
                                if (d.path("reference").asBoolean(false)) {
                                        systemE.add(new Reference());
                                }

                                // Producer?
                                JsonNode prodNode = d.path("producer");
                                if (!prodNode.isMissingNode() && !prodNode.isNull()) {
                                        double interval     = prodNode.path("interval").asDouble(1.0);
                                        int countSquare     = prodNode.path("countSquare").asInt(0);
                                        int countTriangle   = prodNode.path("countTriangle").asInt(0);

                                        if (interval > 0.0) {
                                                Producer producer = new Producer(interval);
                                                producer.remainingSquare   = Math.max(0, countSquare);
                                                producer.remainingTriangle = Math.max(0, countTriangle);
                                                systemE.add(producer);
                                        }
                                }

                                engine.entities().add(systemE);
                                out.systemsById.put(id, systemE);
                        }

                        // 2) Ports
                        for (JsonNode p : root.path("ports")) {
                                String id       = p.path("id").asText();
                                String devId    = p.path("device").asText();
                                double x        = p.path("x").asDouble(0);
                                double y        = p.path("y").asDouble(0);
                                String shapeStr = p.path("shape").asText("square");
                                String ioStr    = p.path("io").asText("in");

                                Entity parentSystem = out.systemsById.get(devId);
                                if (parentSystem == null) {
                                        System.err.println("[LevelLoaderV2] Unknown device for port " + id + ": " + devId);
                                        continue;
                                }

                                PortInfo.Shape shape =
                                        "triangle".equalsIgnoreCase(shapeStr) ? PortInfo.Shape.TRIANGLE : PortInfo.Shape.SQUARE;
                                PortInfo.IO io =
                                        "out".equalsIgnoreCase(ioStr) ? PortInfo.IO.OUT : PortInfo.IO.IN;

                                Entity portE = new Entity();
                                portE.add(new PortInfo(io, shape, parentSystem));
                                portE.add(new Transform(x, y));
                                if (io == PortInfo.IO.IN) {
                                        portE.add(new Queue(5)); // capacity 5 for this phase
                                }

                                engine.entities().add(portE);
                                out.portsById.put(id, portE);

                                // (Optional backward-compat) If someone wrote `"producer": true` on a port,
                                // we DO NOT infer quotas; we recommend defining quotas at device level.
                                if (p.path("producer").asBoolean(false)) {
                                        if (!parentSystem.has(Producer.class)) {
                                                Producer pr = new Producer(p.path("interval").asDouble(1.0));
                                                // Quotas default to 0 (i.e., no production) unless provided at device level
                                                parentSystem.add(pr);
                                        }
                                }

                                // (Optional) "reference": true at port level -> mark system as reference
                                if (p.path("reference").asBoolean(false) && !parentSystem.has(Reference.class)) {
                                        parentSystem.add(new Reference());
                                }
                        }

                        // 3) Links (ignored intentionally for this phase)

                } catch (Exception e) {
                        throw new RuntimeException("Failed to load level from " + path, e);
                }
                return out;
        }
}
