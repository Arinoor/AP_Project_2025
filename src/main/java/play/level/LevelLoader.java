package play.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.components.*;
import play.core.Entity;
import play.system.GameEngine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class LevelLoader {

        public static void loadFromResource(String resource, GameEngine engine) throws Exception {
                InputStream in = LevelLoader.class.getResourceAsStream(resource);
                if (in == null) throw new IllegalArgumentException("Missing level: " + resource);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(in);

                // optional totalWire
                if (root.has("totalWire")) {
                        double tw = root.get("totalWire").asDouble();
                        engine.setTotalWire(tw);
                }

                Map<String, Entity> portsById = new HashMap<>();

                // Ports
                if (root.has("ports")) {
                        for (JsonNode portJson : root.get("ports")) {
                                double x = portJson.get("x").asDouble();
                                double y = portJson.get("y").asDouble();
                                PortInfo.Shape shape = PortInfo.Shape.valueOf(portJson.get("shape").asText().toUpperCase());

                                // Default IO if not in JSON
                                PortInfo.IO io = PortInfo.IO.IN;
                                if (portJson.has("io")) {
                                        io = PortInfo.IO.valueOf(portJson.get("io").asText().toUpperCase());
                                }

                                Entity portEntity = engine.createEntity();
                                portEntity.add(new Transform(x, y));
                                portEntity.add(new PortInfo(io, shape));

                                // Reference flag
                                if (portJson.has("reference") && portJson.get("reference").asBoolean()) {
                                        portEntity.add(new Reference());
                                }

                                // Producer flag + optional interval (seconds)
                                if (portJson.has("producer") && portJson.get("producer").asBoolean()) {
                                        double interval = 0.6; // default
                                        if (portJson.has("interval")) {
                                                interval = Math.max(0.05, portJson.get("interval").asDouble());
                                        }
                                        portEntity.add(new Producer(interval));
                                }

                                String id = portJson.get("id").asText();
                                portsById.put(id, portEntity);
                        }
                }

                // Links
                if (root.has("links")) {
                        for (JsonNode linkJson : root.get("links")) {
                                String fromId = linkJson.get("from").asText();
                                String toId = linkJson.get("to").asText();
                                Entity fromPort = portsById.get(fromId);
                                Entity toPort = portsById.get(toId);

                                if (fromPort == null || toPort == null) continue;

                                Transform ta = fromPort.get(Transform.class);
                                Transform tb = toPort.get(Transform.class);
                                double dx = tb.x - ta.x;
                                double dy = tb.y - ta.y;
                                double length = Math.hypot(dx, dy);

                                // Wire consumption
                                boolean ok = engine.consumeWire(length);
                                if (!ok) {
                                        System.err.println("Not enough wire for link: " + fromId + " -> " + toId);
                                        continue;
                                }

                                Entity linkEntity = engine.createEntity();
                                linkEntity.add(new Link(fromPort, toPort, length));
                        }
                }
        }
}
