package play.system;

import play.core.Entity;
import play.components.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * When a seed reaches the end of its link, detach it and either:
 *  - If destination is Reference: count as delivered, drop the seed
 *  - Else enqueue into destination IN-port Queue (capacity 5)
 * Actual forwarding out of the system happens in QueueSystem.
 */
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

                        Link l = s.currentLink;
                        s.progress += (s.speed * dt + s.accel * dt * dt * 0.5) / Math.max(1.0, l.length);

                        // lateral drift / impact handled elsewhere (Collision/AoE systems)
                        // clamp progress and handle arrival
                        if (s.progress >= 1.0) {
                                // move seed transform to destination port pos
                                if (e.has(Transform.class) && l.toPort != null && l.toPort.has(Transform.class)) {
                                        Transform st = e.get(Transform.class);
                                        Transform tt = l.toPort.get(Transform.class);
                                        st.x = tt.x;
                                        st.y = tt.y;
                                }

                                // detach from link
                                s.currentLink = null;
                                s.progress = 0.0;

                                // if destination system is Reference, mark delivered and remove
                                Entity destSys = l.toPort.get(PortInfo.class).parentSystem;
                                if (destSys.has(Reference.class)) {
                                        engine.notifySeedDelivered(s);
                                        toRemove.add(e);
                                        engine.incrementReachedReference(); // if your engine provides this
                                        continue;
                                }

                                // enqueue into the destination IN-port queue
                                if (l.toPort.has(Queue.class)) {
                                        Queue q = l.toPort.get(Queue.class);
                                        if (!q.isFull()) {
                                                q.push(e);
                                        } else {
                                                // queue full => seed is lost (doc: capacity 5, otherwise it waits,
                                                // but if your design treats overflow as loss, notify here)
                                                toRemove.add(e);
                                                engine.incrementLost();
                                        }
                                } else {
                                        // fallback: create a queue and push
                                        Queue q = new Queue(5);
                                        l.toPort.add(q);
                                        q.push(e);
                                }
                        }
                }

                // remove delivered/lost seeds
                for (Entity e : toRemove) {
                        entities.remove(e);
                }
        }
}
