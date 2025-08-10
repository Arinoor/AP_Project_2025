package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;

public class RoutingSystem implements System {
        private final GameEngine engine;
        private final List<Entity> entities;

        public RoutingSystem(GameEngine engine, List<Entity> entities) {
                this.engine = engine;
                this.entities = entities;
        }

        @Override
        public void update(double dt) {
                List<Entity> toRemove = new ArrayList<>();

                for (Entity e : entities) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        if (s.currentLink == null) continue;
                        if (s.progress < 1.0) continue; // not yet arrived

                        Link l = s.currentLink;

                        // snap to destination
                        if (e.has(Transform.class) && l.toPort != null && l.toPort.has(Transform.class)) {
                                Transform st = e.get(Transform.class);
                                Transform tt = l.toPort.get(Transform.class);
                                st.x = tt.x; st.y = tt.y;
                        }

                        // detach from link
                        s.currentLink = null;
                        s.progress = 0.0;
                        s.lateral = 0.0;
                        s.justCollided = false;

                        Entity destSystem = l.toPort.get(PortInfo.class).parentSystem;
                        if (destSystem.has(Reference.class)) {
                                engine.notifySeedDelivered(s);                 // record delivery
                                engine.incrementReachedReference();            // HUD counter
                                engine.incrementCoins(s.type == Seed.Type.SQUARE ? 1 : 2);
                                toRemove.add(e);
                                continue;
                        }

                        // enqueue into the destination input port (no coins here; reward only on delivery)
                        if (l.toPort.has(Queue.class)) {
                                Queue q = l.toPort.get(Queue.class);
                                if (!q.isFull()) {
                                        q.push(e);
                                } else {
                                        toRemove.add(e);
                                        engine.incrementLost();
                                }
                        } else {
                                Queue q = new Queue(5);
                                l.toPort.add(q);
                                q.push(e);
                        }
                }

                for (Entity e : toRemove) entities.remove(e);
        }
}
