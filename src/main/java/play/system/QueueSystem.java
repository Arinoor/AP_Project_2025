package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves queued seeds from an input port's queue onto a free outgoing link of the same system.
 * Priority: matching shape first; else any free link. If no free link, stays queued.
 */
public class QueueSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;

        public QueueSystem(GameEngine engine, List<Entity> entities) {
                this.engine = engine;
                this.entities = entities;
        }

        @Override
        public void update(double dt) {
                // iterate over all input ports that have a Queue component
                for (Entity portE : entities) {
                        if (!portE.has(PortInfo.class) || !portE.has(Queue.class)) continue;

                        PortInfo pinfo = portE.get(PortInfo.class);
                        if (pinfo.io != PortInfo.IO.IN) continue;

                        Queue q = portE.get(Queue.class);
                        if (q.isEmpty()) continue;

                        // find the parent system of this port
                        Entity systemE = pinfo.parentSystem;
                        if (systemE == null) continue;

                        // collect outgoing links from this system
                        List<Entity> candidateLinks = new ArrayList<>();
                        for (Entity linkE : entities) {
                                if (!linkE.has(Link.class)) continue;
                                Link linkC = linkE.get(Link.class);
                                if (linkC.fromPort == null || linkC.toPort == null) continue;

                                // this link is from this system and is currently free (no seed on it)
                                if (linkC.fromPort.get(PortInfo.class).parentSystem == systemE && isLinkFree(linkE)) {
                                        candidateLinks.add(linkE);
                                }
                        }
                        if (candidateLinks.isEmpty()) continue;

                        // peek seed
                        Entity seedE = q.peek();
                        if (seedE == null || !seedE.has(Seed.class)) continue;
                        Seed s = seedE.get(Seed.class);

                        // pick a best link: first try shape match on toPort, else any
                        Entity best = pickBestLinkForSeed(candidateLinks, s);

                        if (best != null) {
                                q.pop(); // actually dequeue one
                                attachSeedToLink(seedE, best);
                        }
                }
        }

        private Entity pickBestLinkForSeed(List<Entity> links, Seed s) {
                // prefer equal shape at toPort
                for (Entity linkE : links) {
                        Link l = linkE.get(Link.class);
                        PortInfo toInfo = l.toPort.get(PortInfo.class);
                        if (matches(s, toInfo.shape)) return linkE;
                }
                // else any free
                return links.get(0);
        }

        private boolean matches(Seed s, PortInfo.Shape shape) {
                return (s.type == Seed.Type.SQUARE && shape == PortInfo.Shape.SQUARE)
                        || (s.type == Seed.Type.TRIANGLE && shape == PortInfo.Shape.TRIANGLE);
        }

        private boolean isLinkFree(Entity linkE) {
                Link linkC = linkE.get(Link.class);
                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == linkC) { // compare component-to-component
                                return false;
                        }
                }
                return true;
        }

        private void attachSeedToLink(Entity seedE, Entity linkE) {
                Seed s = seedE.get(Seed.class);
                Link l = linkE.get(Link.class);

                s.currentLink = l;     // NOTE: currentLink is a Link component, not an Entity
                s.progress = 0.0;

                // optional: reset lateral/impact on re-attach
                s.lateral = 0.0;
                s.impactEnergy = 0.0;

                // position the seed at fromPort transform
                if (l.fromPort != null && l.fromPort.has(Transform.class) && seedE.has(Transform.class)) {
                        Transform pt = l.fromPort.get(Transform.class);
                        Transform st = seedE.get(Transform.class);
                        st.x = pt.x;
                        st.y = pt.y;
                }
        }
}
