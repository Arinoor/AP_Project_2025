package play.model.level;

import play.model.components.*;
import play.model.core.Entity;
import play.model.engine.GameEngine;

import java.util.HashMap;
import java.util.Map;

/** Builds engine entities from a LevelDto. No links are created. */
public final class LevelFactory {
        private LevelFactory() {}

        public static Result applyToEngine(GameEngine engine, LevelDto dto) {
                engine.resetForLevel();

                Map<String, Entity> systemsById = new HashMap<>();
                Map<String, Entity> portsById   = new HashMap<>();

                int plannedSeeds = 0;

                // Devices
                for (LevelDto.DeviceDto d : dto.devices) {
                        Entity systemE = new Entity();
                        systemE.add(new Transform(d.x, d.y));
                        if (d.reference) systemE.add(new Reference());

                        if (d.producer != null) {
                                int qS = d.producer.quotaSquare == null ? -1 : d.producer.quotaSquare;
                                int qT = d.producer.quotaTriangle == null ? -1 : d.producer.quotaTriangle;
                                if (qS > 0) plannedSeeds += qS;
                                if (qT > 0) plannedSeeds += qT;
                                systemE.add(new Producer(d.producer.interval, qS, qT));
                        }

                        engine.entities().add(systemE);
                        systemsById.put(d.id, systemE);
                }

                // Ports
                for (LevelDto.PortDto p : dto.ports) {
                        Entity parentSystem = systemsById.get(p.deviceId);
                        if (parentSystem == null) {
                                System.err.println("[LevelFactory] Unknown device for port " + p.id + ": " + p.deviceId);
                                continue;
                        }

                        PortInfo.Shape shape = "triangle".equalsIgnoreCase(p.shape) ? PortInfo.Shape.TRIANGLE : PortInfo.Shape.SQUARE;
                        PortInfo.IO io = "out".equalsIgnoreCase(p.io) ? PortInfo.IO.OUT : PortInfo.IO.IN;

                        Entity portE = new Entity();
                        portE.add(new PortInfo(io, shape, parentSystem));
                        portE.add(new Transform(p.x, p.y));
                        if (io == PortInfo.IO.IN) {
                                portE.add(new Queue(5)); // capacity is still 5 (phase rule); can be parameterized later
                        }

                        engine.entities().add(portE);
                        portsById.put(p.id, portE);

                        // Optional: port-level producer
                        if (p.producer) {
                                double interval = (p.interval != null) ? p.interval : 1.0;
                                if (!parentSystem.has(Producer.class) && interval > 0.0) {
                                        parentSystem.add(new Producer(interval));
                                }
                        }

                        // Optional: port-level reference
                        if (p.reference && !parentSystem.has(Reference.class)) {
                                parentSystem.add(new Reference());
                        }
                }

                // No links created here by design
                return new Result(dto.totalWire, dto.timeLimitSeconds, plannedSeeds);
        }

        public record Result(double totalWire, int timeLimitSeconds, int plannedSeeds) {}
}
