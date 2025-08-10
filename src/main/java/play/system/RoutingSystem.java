package play.system;

import play.components.Link;
import play.components.PortInfo;
import play.components.Seed;
import play.components.Transform;
import play.core.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Chooses a next link for a seed at a port. Prefers links whose destination port shape
 * matches the seed's shape; otherwise falls back to any available outgoing link.
 */
public class RoutingSystem {

        private final List<Entity> world;

        public RoutingSystem(List<Entity> world) {
                this.world = world;
        }

        /** Try to route the seed from 'atPort' onto a next link. Returns true if routed. */
        public boolean route(Seed seed, Entity atPort) {
                // find outgoing links where fromPort == atPort
                List<Entity> outgoing = new ArrayList<>();
                for (Entity e : world) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (Objects.equals(l.fromPort, atPort)) {
                                outgoing.add(e);
                        }
                }
                if (outgoing.isEmpty()) return false;

                // prefer a link whose destination port shape matches seed type
                Entity best = null;
                for (Entity link : outgoing) {
                        Link l = link.get(Link.class);
                        if (l.toPort != null && l.toPort.has(PortInfo.class)) {
                                PortInfo.Shape destShape = l.toPort.get(PortInfo.class).shape;
                                if (matches(seed.type, destShape)) { best = link; break; }
                        }
                }
                if (best == null) best = outgoing.get(0);

                // attach the seed to that link and snap to port position
                Link chosen = best.get(Link.class);
                if (chosen.fromPort != null && chosen.fromPort.has(Transform.class)) {
                        Transform pt = chosen.fromPort.get(Transform.class);
                        // move seed to the port location
                        if (seed != null && seed.currentLink != null && seed.currentLink.has(Transform.class)) {
                                // nothing special; we store position on the seed entity's Transform
                        }
                }
                seed.currentLink = best;
                seed.progress = 0.0;
                return true;
        }

        private boolean matches(Seed.Type t, PortInfo.Shape s) {
                return (t == Seed.Type.SQUARE && s == PortInfo.Shape.SQUARE)
                        || (t == Seed.Type.TRIANGLE && s == PortInfo.Shape.TRIANGLE);
        }
}
