package play.model.services;

import play.model.components.Link;
import play.model.components.PortInfo;
import play.model.components.Transform;
import play.model.core.Entity;

import java.util.*;

/** Pure helpers for wiring validation and link operations. */
public final class WiringService {
        private WiringService() {}

        public static boolean allPortsFilled(List<Entity> entities) {
                Map<Entity, Integer> outDeg = new HashMap<>();
                Map<Entity, Integer> inDeg  = new HashMap<>();
                for (Entity e : entities) if (e.has(Link.class)) {
                        Link l = e.get(Link.class);
                        outDeg.merge(l.fromPort, 1, Integer::sum);
                        inDeg.merge(l.toPort, 1, Integer::sum);
                }
                for (Entity e : entities) {
                        if (!e.has(PortInfo.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        if (p.io == PortInfo.IO.OUT && outDeg.getOrDefault(e, 0) != 1) return false;
                        if (p.io == PortInfo.IO.IN  && inDeg.getOrDefault(e, 0)  != 1) return false;
                }
                return true;
        }

        public static boolean isGraphConnected(List<Entity> entities) {
                List<Entity> systems = new ArrayList<>();
                for (Entity e : entities)
                        if (e.has(Transform.class) && !e.has(PortInfo.class) && !e.has(Link.class)) systems.add(e);
                if (systems.isEmpty()) return true;

                Map<Entity, Set<Entity>> adj = new HashMap<>();
                for (Entity s : systems) adj.put(s, new HashSet<>());
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        Entity A = l.fromPort.get(PortInfo.class).parentSystem;
                        Entity B = l.toPort.get(PortInfo.class).parentSystem;
                        adj.get(A).add(B);
                        adj.get(B).add(A);
                }

                Set<Entity> seen = new HashSet<>();
                Deque<Entity> dq = new ArrayDeque<>();
                dq.add(systems.get(0));
                seen.add(systems.get(0));
                while (!dq.isEmpty()) {
                        Entity u = dq.pollFirst();
                        for (Entity v : adj.getOrDefault(u, Set.of())) if (seen.add(v)) dq.add(v);
                }
                return seen.size() == systems.size();
        }

        public static List<Entity> collectOutgoingLinks(List<Entity> entities, Entity fromPort) {
                List<Entity> out = new ArrayList<>();
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        if (e.get(Link.class).fromPort == fromPort) out.add(e);
                }
                return out;
        }

        public static List<Entity> collectIncomingLinks(List<Entity> entities, Entity toPort) {
                List<Entity> out = new ArrayList<>();
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        if (e.get(Link.class).toPort == toPort) out.add(e);
                }
                return out;
        }

        /** Sum straight-line lengths for given links (requires port transforms). */
        public static double wireLengthForLinks(List<Entity> links) {
                double total = 0.0;
                for (Entity e : links) {
                        Link l = e.get(Link.class);
                        if (l.fromPort != null && l.toPort != null &&
                                l.fromPort.has(Transform.class) && l.toPort.has(Transform.class)) {
                                Transform a = l.fromPort.get(Transform.class);
                                Transform b = l.toPort.get(Transform.class);
                                total += Math.hypot(b.x - a.x, b.y - a.y);
                        }
                }
                return total;
        }
}
