package play.controller;

import play.model.components.Link;
import play.model.components.PortInfo;
import play.model.components.Transform;
import play.model.core.Entity;
import play.utils.WiringUtils;

import java.util.*;

/**
 * WiringService (slim)
 * --------------------
 * Keeps ONLY graph/port validation and rewire operations.
 * All geometry, lengths, bends, and crossing checks are handled in WiringUtils.
 */
public final class WiringService {

        private WiringService() {}

        // ---------- Port & graph checks ----------

        /** True if every OUT port has exactly one outgoing link and every IN port has exactly one incoming link. */
        public static boolean allPortsFilled(List<Entity> entities) {
                Map<Entity, Integer> outDeg = new HashMap<>();
                Map<Entity, Integer> inDeg  = new HashMap<>();
                for (Entity e : entities) if (e.has(Link.class)) {
                        Link l = e.get(Link.class);
                        outDeg.merge(l.fromPort, 1, Integer::sum);
                        inDeg .merge(l.toPort,   1, Integer::sum);
                }
                for (Entity e : entities) {
                        if (!e.has(PortInfo.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        if (p.io == PortInfo.IO.OUT && outDeg.getOrDefault(e, 0) != 1) return false;
                        if (p.io == PortInfo.IO.IN  && inDeg .getOrDefault(e, 0) != 1) return false;
                }
                return true;
        }

        /** Undirected connectivity across systems (treats links as undirected edges between parent systems). */
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
                        Entity B = l.toPort  .get(PortInfo.class).parentSystem;
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

        // ---------- Wire length helpers (delegate to utils) ----------

        /** Straight distance between two ports (used for newly created links with no bends yet). */
        public static double linkLengthStraight(Entity fromPort, Entity toPort) {
                Transform a = fromPort.get(Transform.class);
                Transform b = toPort  .get(Transform.class);
                return Math.hypot(b.x - a.x, b.y - a.y);
        }

        /** Total length of all links using current bends (polyline-aware). */
        public static double totalWireLength(List<Entity> entities) {
                return WiringUtils.totalWireLength(entities);
        }

        /** Whether ANY link crosses ANY system rectangle (excluding its two endpoint systems). */
        public static boolean hasAnySystemCrossing(List<Entity> entities, double systemSizePx) {
                return WiringUtils.hasAnySystemCrossing(entities, systemSizePx);
        }

        // ---------- Rewiring ----------

        public static final class WiringComputation {
                public final boolean valid;
                public final String reason;
                public final Entity fromPort;
                public final Entity toPort;
                public final double newWireLen;   // straight initial link
                public final double removedLen;

                public WiringComputation(boolean valid, String reason, Entity fromPort, Entity toPort,
                                         double newWireLen, double removedLen) {
                        this.valid = valid; this.reason = reason;
                        this.fromPort = fromPort; this.toPort = toPort;
                        this.newWireLen = newWireLen; this.removedLen = removedLen;
                }

                public double netIncrease() { return newWireLen - removedLen; }
        }

        /** Validate the two ports are compatible for wiring. */
        public static String validatePorts(Entity fromPort, Entity toPort) {
                if (fromPort == null || toPort == null) return "missing port(s)";
                if (!fromPort.has(PortInfo.class) || !toPort.has(PortInfo.class)) return "not a port";
                PortInfo a = fromPort.get(PortInfo.class);
                PortInfo b = toPort.get(PortInfo.class);
                if (a.io != PortInfo.IO.OUT || b.io != PortInfo.IO.IN) return "wrong IO direction";
                if (a.parentSystem == b.parentSystem) return "same system";
                if (a.shape != b.shape) return "shape mismatch";
                if (!fromPort.has(Transform.class) || !toPort.has(Transform.class)) return "missing transforms";
                return null;
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

        /** Compute the rewire plan (removed lengths + new straight length). */
        public static WiringComputation compute(List<Entity> entities, Entity fromPort, Entity toPort) {
                String err = validatePorts(fromPort, toPort);
                if (err != null) return new WiringComputation(false, err, fromPort, toPort, 0, 0);

                List<Entity> outLinks = collectOutgoingLinks(entities, fromPort);
                List<Entity> inLinks  = collectIncomingLinks(entities, toPort);

                double removed = WiringUtils.totalWireLength(outLinks) + WiringUtils.totalWireLength(inLinks);
                double added   = linkLengthStraight(fromPort, toPort);
                return new WiringComputation(true, null, fromPort, toPort, added, removed);
        }

        /** Perform the rewire: remove conflicting links and add a new straight Link(from,to). */
        public static Entity performRewire(List<Entity> entities, Entity fromPort, Entity toPort) {
                List<Entity> outLinks = collectOutgoingLinks(entities, fromPort);
                List<Entity> inLinks  = collectIncomingLinks(entities, toPort);
                if (!outLinks.isEmpty()) entities.removeAll(outLinks);
                if (!inLinks.isEmpty())  entities.removeAll(inLinks);

                Entity linkE = new Entity().add(new Link(fromPort, toPort));
                entities.add(linkE);
                return linkE;
        }
}
