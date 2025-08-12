package play.model.level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public final class LevelParser {
        private static final ObjectMapper M = new ObjectMapper();

        private LevelParser() {}

        public static LevelDto parseFromResource(String path) {
                try (InputStream in = LevelParser.class.getResourceAsStream(path)) {
                        if (in == null) throw new IllegalArgumentException("Missing level resource: " + path);
                        JsonNode root = M.readTree(in);
                        LevelDto dto = new LevelDto();
                        dto.totalWire = root.path("totalWire").asDouble(3000.0);
                        dto.timeLimitSeconds = root.path("timeLimitSeconds").asInt(120);

                        for (JsonNode d : root.path("devices")) {
                                LevelDto.DeviceDto dev = new LevelDto.DeviceDto();
                                dev.id = d.path("id").asText();
                                dev.x = d.path("x").asDouble(0);
                                dev.y = d.path("y").asDouble(0);
                                dev.reference = d.path("reference").asBoolean(false);

                                JsonNode prodNode = d.path("producer");
                                if (!prodNode.isMissingNode() && !prodNode.isNull()) {
                                        LevelDto.ProducerDto p = new LevelDto.ProducerDto();
                                        p.interval = prodNode.path("interval").asDouble(1.0);
                                        JsonNode q = prodNode.path("quota");
                                        if (!q.isMissingNode() && !q.isNull()) {
                                                if (q.has("square"))   p.quotaSquare = q.path("square").isNull() ? null : q.path("square").asInt();
                                                if (q.has("triangle")) p.quotaTriangle = q.path("triangle").isNull() ? null : q.path("triangle").asInt();
                                        }
                                        dev.producer = p;
                                }
                                dto.devices.add(dev);
                        }

                        for (JsonNode p : root.path("ports")) {
                                LevelDto.PortDto port = new LevelDto.PortDto();
                                port.id = p.path("id").asText();
                                port.deviceId = p.path("device").asText();
                                port.x = p.path("x").asDouble(0);
                                port.y = p.path("y").asDouble(0);
                                port.shape = p.path("shape").asText("square");
                                port.io = p.path("io").asText("in");
                                port.reference = p.path("reference").asBoolean(false);
                                port.producer = p.path("producer").asBoolean(false);
                                if (port.producer) {
                                        port.interval = p.path("interval").isMissingNode() ? null : p.path("interval").asDouble(1.0);
                                }
                                dto.ports.add(port);
                        }

                        return dto;
                } catch (Exception e) {
                        throw new RuntimeException("Failed to parse level: " + path, e);
                }
        }
}
