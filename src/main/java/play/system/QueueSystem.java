package play.system;

import play.components.*;
import play.core.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Dequeues from devices to FREE outgoing links; prefer compatible shape else any free. */
public class QueueSystem implements System {

        private final List<Entity> world;
        private final Random rng = new Random();

        public QueueSystem(List<Entity> world) { this.world = world; }

        @Override
        public void update(double dt) {
                for (Entity devE : world) {
                        if (!devE.has(Device.class)) continue;
                        Device dev = devE.get(Device.class);
                        if (dev.isEmpty()) continue;

                        // collect free outgoing links
                        List<Entity> freeLinks = new ArrayList<>();
                        for (Entity outP : dev.outputPorts) {
                                for (Entity e : world) {
                                        if (!e.has(Link.class)) continue;
                                        Link L = e.get(Link.class);
                                        if (!Objects.equals(L.fromPort, outP)) continue;
                                        if (isLinkFree(e)) freeLinks.add(e);
                                }
                        }
                        if (freeLinks.isEmpty()) continue;

                        Entity seedE = dev.peek();
                        Seed s = seedE.get(Seed.class);

                        // choose compatible free link first
                        Entity chosen = null;
                        for (Entity linkE : freeLinks) {
                                PortInfo.Shape destShape = linkE.get(Link.class).toPort.get(PortInfo.class).shape;
                                if (matches(s.type, destShape)) { chosen = linkE; break; }
                        }
                        if (chosen == null) chosen = freeLinks.get(rng.nextInt(freeLinks.size()));

                        // pop & launch
                        dev.pop();
                        Link L = chosen.get(Link.class);
                        boolean startCompatible = matches(s.type, L.fromPort.get(PortInfo.class).shape);
                        applyStartKinematics(s, startCompatible);
                        s.currentLink = chosen;
                        s.progress = 0.0;

                        // snap seed to fromPort position
                        Transform ts = seedE.get(Transform.class);
                        Transform tFrom = L.fromPort.get(Transform.class);
                        ts.x = tFrom.x; ts.y = tFrom.y;
                }
        }

        private boolean isLinkFree(Entity link) {
                for (Entity e : world) {
                        if (!e.has(Seed.class)) continue;
                        if (e.get(Seed.class).currentLink == link) return false;
                }
                return true;
        }

        private boolean matches(Seed.Type t, PortInfo.Shape s) {
                return (t == Seed.Type.SQUARE && s == PortInfo.Shape.SQUARE)
                        || (t == Seed.Type.TRIANGLE && s == PortInfo.Shape.TRIANGLE);
        }

        private void applyStartKinematics(Seed s, boolean startCompatible) {
                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;                      // choose good constants
                        s.speed = startCompatible ? base * 0.5 : base;
                        s.accel = 0.0;
                } else { // TRIANGLE
                        double base = 140.0;
                        s.speed = base;
                        s.accel = startCompatible ? 0.0 : 220.0;  // accelerate when incompatible
                }
        }
}
