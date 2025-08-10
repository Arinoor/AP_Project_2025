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
 * Loads ONLY systems and ports from JSON. Does NOT create any links.
 * The player will create links at runtime via the wiring tool.
 *
 * Supported JSON (links are ignored if present):
 * {
 *   "totalWire": 5000,
 *   "devices": [
 *     { "id": "REF-A", "capacity": 5, "x": 120, "y": 220, "reference": false,
 *       "producer": { "interval": 0.6 } }
 *   ],
 *   "ports": [
 *     { "id": "A:outS", "device": "REF-A", "x": 180, "y": 220,
 *       "shape": "square", "io": "out" },
 *     { "id": "A:inT",  "device": "REF-A", "x":  60, "y": 260,
 *       "shape": "triangle", "io": "in" }
 *   ],
 *   "links": [ ... ] // optional and IGNORED
 * }
 *
 * Notes:
 * - If "producer" exists on a device OR any of its ports has "producer": true,
 *   a Producer component is added to the device (interval default 1.0).
 * - If "reference": true on a device, we add a Reference component to it.
 * - Every IN port gets a Queue(5).
 * - Port coordinates are absolute screen positions (x, y).
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

                                // reference?
                                if (d.path("reference").asBoolean(false)) {
                                        systemE.add(new Reference());
                                }

                                // producer on device?
                                JsonNode prodNode = d.path("producer");
                                double interval = 0.0;
                                if (!prodNode.isMissingNode() && !prodNode.isNull()) {
                                        interval = prodNode.path("interval").asDouble(1.0);
                                }
                                if (interval > 0.0) {
                                        systemE.add(new Producer(interval));
                                }

                                engine.entities().add(systemE);
                                out.systemsById.put(id, systemE);
                        }

                        // 2) Ports
                        // If a port has "producer": true here, we attach a Producer to the parent device
                        // (useful when producer is declared at port-level instead of device-level).
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

                                // Optional: producer specified at port level
                                if (p.path("producer").asBoolean(false)) {
                                        double interval = p.path("interval").asDouble(1.0);
                                        if (!parentSystem.has(Producer.class) && interval > 0.0) {
                                                parentSystem.add(new Producer(interval));
                                        }
                                }

                                // Optional: reference specified at port level (rare, but allow it)
                                if (p.path("reference").asBoolean(false) && !parentSystem.has(Reference.class)) {
                                        parentSystem.add(new Reference());
                                }
                        }

                        // 3) Links (if present) are intentionally IGNORED for this phase
                        // root.path("links") is not processed.

                } catch (Exception e) {
                        throw new RuntimeException("Failed to load level from " + path, e);
                }
                return out;
        }
}
