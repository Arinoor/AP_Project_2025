package play.level;

import com.fasterxml.jackson.databind.ObjectMapper;
import play.core.Entity;
import play.system.GameEngine;
import play.components.*;

import java.io.InputStream;
import java.util.*;

/**
 * Loads JSON level files into the engine.
 * Expected JSON schema (example provided later).
 */
public final class LevelLoader {

        public static void loadFromResource(String resourcePath, GameEngine engine) throws Exception {
                ObjectMapper mapper = new ObjectMapper();
                try (InputStream is = LevelLoader.class.getResourceAsStream(resourcePath)) {
                        if (is == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);
                        LevelSpec spec = mapper.readValue(is, LevelSpec.class);

                        // maps for quick lookup
                        Map<String, Entity> conduitMap = new HashMap<>();
                        Map<String, Entity> portMap = new HashMap<>();
                        List<Entity> linkList = new ArrayList<>();

                        // create conduits and ports
                        if (spec.conduits != null) {
                                for (LevelSpec.Conduit c : spec.conduits) {
                                        Entity ce = engine.createEntity();
                                        ce.add(new Transform(c.x, c.y));
                                        conduitMap.put(c.id, ce);

                                        // create default ports: id-in and id-out
                                        Entity in = engine.createEntity();
                                        in.add(new Transform(c.x - 40, c.y));
                                        in.add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.SQUARE)); // default; can extend JSON later
                                        portMap.put(c.id + "-in", in);

                                        Entity out = engine.createEntity();
                                        out.add(new Transform(c.x + 40, c.y));
                                        out.add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.SQUARE));
                                        portMap.put(c.id + "-out", out);
                                }
                        }

                        // create links
                        if (spec.links != null) {
                                for (LevelSpec.Link l : spec.links) {
                                        Entity from = portMap.get(l.from);
                                        Entity to = portMap.get(l.to);
                                        if (from == null || to == null) continue;
                                        Entity linkE = engine.createEntity();

                                        Transform ta = from.get(Transform.class);
                                        Transform tb = to.get(Transform.class);
                                        double dx = tb.x - ta.x, dy = tb.y - ta.y;
                                        double len = Math.hypot(dx, dy);
                                        linkE.add(new Link(from, to, len));
                                        linkList.add(linkE);
                                }
                        }

                        // create initial seeds
                        if (spec.initialSeeds != null) {
                                for (LevelSpec.SeedSpec s : spec.initialSeeds) {
                                        Entity seedE = engine.createEntity();
                                        seedE.add(new Transform(0, 0));
                                        Seed.Type type = "TRIANGLE".equalsIgnoreCase(s.type) ? Seed.Type.TRIANGLE : Seed.Type.SQUARE;
                                        Seed sd = new Seed(type);
                                        // attach to link index (safe check)
                                        int idx = Math.max(0, Math.min(s.onLink, linkList.size() - 1));
                                        if (!linkList.isEmpty()) {
                                                sd.currentLink = linkList.get(idx);
                                                sd.progress = s.progress;
                                                // position will be set by movement system on next tick
                                        }
                                        seedE.add(sd);
                                }
                        }
                }
        }

        // simple holder classes for Jackson
        public static class LevelSpec {
                public String name;
                public List<Conduit> conduits;
                public List<Link> links;
                public List<SeedSpec> initialSeeds;

                public static class Conduit { public String id; public double x; public double y; }
                public static class Link { public String from; public String to; }
                public static class SeedSpec { public String type; public int onLink; public double progress; }
        }
}
