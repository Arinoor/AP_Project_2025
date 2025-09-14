package play.model.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.model.components.*;
import play.model.core.Entity;
import play.model.engine.GameEngine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads ONLY systems and ports from JSON. No links are created here.
 *
 * Quotas supported:
 *  - devices[].producer.quota.{square,triangle,infinite,secure}
 * Device flags:
 *  - devices[].reference: true  -> adds Reference component
 *  - devices[].vpn: true        -> adds Vpn component (+ BackgroundImage("/img/vpn_system.png"))
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
                        if (in == null) throw new IllegalArgumentException("Missing level resource: " + path);
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

                                if (d.path("reference").asBoolean(false)) {
                                        systemE.add(new Reference());
                                }
                                if (d.path("vpn").asBoolean(false)) {
                                        systemE.add(new Vpn());
                                        systemE.add(new BackgroundImage("/img/vpn_system.png"));
                                }

                                if (d.path("spy").asBoolean(false)) {
                                        systemE.add(new Spy());
                                        systemE.add(new BackgroundImage("/img/spy_system.png"));
                                }

                                JsonNode prodNode = d.path("producer");
                                if (!prodNode.isMissingNode() && !prodNode.isNull()) {
                                        double interval = prodNode.path("interval").asDouble(1.0);

                                        int qS = -1, qT = -1, qI = 0, qC = 0; // default new types to 0 unless provided
                                        JsonNode quota = prodNode.path("quota");
                                        if (!quota.isMissingNode() && !quota.isNull()) {
                                                if (quota.has("square"))   qS = quota.path("square").asInt(-1);
                                                if (quota.has("triangle")) qT = quota.path("triangle").asInt(-1);
                                                if (quota.has("infinite")) qI = quota.path("infinite").asInt(0);
                                                if (quota.has("secure"))   qC = quota.path("secure").asInt(0);

                                                // Sum only finite quotas into planned total
                                                if (qS > 0) out.plannedSeeds += qS;
                                                if (qT > 0) out.plannedSeeds += qT;
                                                if (qI > 0) out.plannedSeeds += qI;
                                                if (qC > 0) out.plannedSeeds += qC;

                                                if (quota.has("secure")) {
                                                        systemE.add(new Producer(interval, qS, qT, qI, qC));
                                                } else if (quota.has("infinite")) {
                                                        systemE.add(new Producer(interval, qS, qT, qI));
                                                } else {
                                                        systemE.add(new Producer(interval, qS, qT));
                                                }
                                        } else {
                                                // Defaults: new types infinite/secure = 0 so they don't spawn unless declared
                                                Producer p = new Producer(interval);
                                                p.remainingInfinite = 0;
                                                p.remainingSecure   = 0;
                                                systemE.add(p);
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
                                        portE.add(new Queue(5)); // capacity for this phase
                                }

                                if (p.path("reference").asBoolean(false) && !parentSystem.has(Reference.class)) {
                                        parentSystem.add(new Reference());
                                }

                                engine.entities().add(portE);
                                out.portsById.put(id, portE);
                        }

                        // 3) Links: ignored on load (player wires them)
                } catch (Exception e) {
                        throw new RuntimeException("Failed to load level from " + path, e);
                }
                return out;
        }
}
