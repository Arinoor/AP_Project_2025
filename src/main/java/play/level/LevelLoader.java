package play.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.components.*;
import play.core.Entity;
import play.system.GameEngine;

import java.io.InputStream;
import java.util.*;

public class LevelLoader {

        public static void loadFromResource(String resource, GameEngine engine) throws Exception {
                InputStream in = LevelLoader.class.getResourceAsStream(resource);
                if (in == null) throw new IllegalArgumentException("Missing level: " + resource);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(in);

                if (root.has("totalWire")) engine.setTotalWire(root.get("totalWire").asDouble());

                Map<String, Entity> devicesById = new HashMap<>();
                Map<String, List<Entity>> inputsByDev = new HashMap<>();
                Map<String, List<Entity>> outputsByDev = new HashMap<>();
                Map<String, Entity> portsById = new HashMap<>();

                // Devices (optional)
                if (root.has("devices")) {
                        for (JsonNode d : root.get("devices")) {
                                String id = d.get("id").asText();
                                int cap = d.has("capacity") ? d.get("capacity").asInt(5) : 5;
                                Entity devE = engine.createEntity();
                                devE.add(new Device(id, cap));
                                devicesById.put(id, devE);
                                inputsByDev.put(id, new ArrayList<>());
                                outputsByDev.put(id, new ArrayList<>());
                        }
                }

                // Ports
                if (root.has("ports")) {
                        for (JsonNode p : root.get("ports")) {
                                String id = p.get("id").asText();
                                double x = p.get("x").asDouble();
                                double y = p.get("y").asDouble();
                                PortInfo.Shape shape = PortInfo.Shape.valueOf(p.get("shape").asText().toUpperCase());
                                PortInfo.IO io = PortInfo.IO.valueOf(p.get("io").asText().toUpperCase());

                                Entity portE = engine.createEntity();
                                portE.add(new Transform(x, y));
                                portE.add(new PortInfo(io, shape));
                                if (p.path("reference").asBoolean(false)) portE.add(new Reference());
                                if (p.path("producer").asBoolean(false)) {
                                        double interval = p.has("interval") ? p.get("interval").asDouble() : 0.6;
                                        portE.add(new Producer(interval));
                                }

                                // device binding
                                String devId = p.has("device") ? p.get("device").asText() : id.split(":")[0];
                                Entity devE = devicesById.computeIfAbsent(devId, k -> {
                                        Entity dE = engine.createEntity();
                                        dE.add(new Device(k, 5));
                                        inputsByDev.put(k, new ArrayList<>());
                                        outputsByDev.put(k, new ArrayList<>());
                                        return dE;
                                });
                                portE.add(new DeviceRef(devE));
                                if (io == PortInfo.IO.IN)  inputsByDev.get(devId).add(portE);
                                if (io == PortInfo.IO.OUT) outputsByDev.get(devId).add(portE);

                                portsById.put(id, portE);
                        }
                }

                // backfill port lists into devices
                for (Map.Entry<String, Entity> e : devicesById.entrySet()) {
                        Device d = e.getValue().get(Device.class);
                        d.inputPorts = inputsByDev.get(e.getKey());
                        d.outputPorts = outputsByDev.get(e.getKey());
                }

                // Links
                if (root.has("links")) {
                        for (JsonNode link : root.get("links")) {
                                Entity from = portsById.get(link.get("from").asText());
                                Entity to   = portsById.get(link.get("to").asText());
                                if (from == null || to == null) continue;

                                Transform ta = from.get(Transform.class);
                                Transform tb = to.get(Transform.class);
                                double len = Math.hypot(tb.x - ta.x, tb.y - ta.y);

                                if (!engine.consumeWire(len)) {
                                        System.err.println("Not enough wire for link " + link);
                                        continue;
                                }

                                Entity L = engine.createEntity();
                                L.add(new Link(from, to, len));
                        }
                }
        }
}
