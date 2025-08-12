package play.model.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.model.components.*;
import play.model.core.Entity;
import play.model.components.*;
import play.model.engine.GameEngine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * LevelLoaderV2
 *
 * Loads ONLY systems and ports from JSON. Does NOT create any links.
 * The player will create links at runtime via the wiring tool.
 *
 * JSON additions:
 *  - timeLimitSeconds: int, optional (default 120)
 *  - device.producer.quota.square / triangle: per-device fixed counts
 *
 * Example:
 * {
 *   "timeLimitSeconds": 90,
 *   "totalWire": 5000,
 *   "devices": [
 *     { "id":"SRC-1","x":150,"y":240,
 *       "producer":{"interval":0.6,"quota":{"square":10,"triangle":10}} }
 *   ],
 *   "ports": [ ... ]
 * }
 */
public final class LevelLoaderV2 {

        public static final class Loaded {
                public double totalWire = 3000;
                public int timeLimitSeconds = 120;
                public int plannedSeeds = 0;
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
                        out.timeLimitSeconds = root.path("timeLimitSeconds").asInt(120);

                        // 1) Devices (systems)
                        for (JsonNode d : root.path("devices")) {
                                String id = d.path("id").asText();
                                double x  = d.path("x").asDouble(0);
                                double y  = d.path("y").asDouble(0);

                                Entity systemE = new Entity();
                                systemE.add(new Transform(x, y));

                                // reference?
                                if (d.path("reference").asBoolean(false)) {
                                        systemE.add(new Reference());
                                }

                                // producer on device?
                                JsonNode prodNode = d.path("producer");
                                if (!prodNode.isMissingNode() && !prodNode.isNull()) {
                                        double interval = prodNode.path("interval").asDouble(1.0);

                                        // optional quotas
                                        int qS = -1;
                                        int qT = -1;
                                        JsonNode quota = prodNode.path("quota");
                                        if (!quota.isMissingNode() && !quota.isNull()) {
                                                if (quota.has("square"))   qS = quota.path("square").asInt(-1);
                                                if (quota.has("triangle")) qT = quota.path("triangle").asInt(-1);

                                                // Sum only finite quotas into planned total
                                                if (qS > 0) out.plannedSeeds += qS;
                                                if (qT > 0) out.plannedSeeds += qT;

                                                systemE.add(new Producer(interval, qS, qT));
                                        } else {
                                                systemE.add(new Producer(interval));
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

                                // Optional: producer specified at port level (rare)
                                if (p.path("producer").asBoolean(false)) {
                                        double interval = p.path("interval").asDouble(1.0);
                                        if (!parentSystem.has(Producer.class) && interval > 0.0) {
                                                parentSystem.add(new Producer(interval));
                                        }
                                }

                                // Optional: reference specified at port level
                                if (p.path("reference").asBoolean(false) && !parentSystem.has(Reference.class)) {
                                        parentSystem.add(new Reference());
                                }
                        }

                        // 3) Links (ignored on load)
                } catch (Exception e) {
                        throw new RuntimeException("Failed to load level from " + path, e);
                }
                return out;
        }
}
