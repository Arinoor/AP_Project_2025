package play.system;

import play.core.Entity;
import play.components.Seed;
import play.components.Transform;

import java.util.ArrayList;
import java.util.List;

/**
 * GLOBAL pairwise collision detection. Any two seeds that overlap will
 * bump each other laterally and increment collision counters.
 * Seeds are lost if |lateral| > threshold or collision count >= capacity.
 */
public class CollisionSystem implements System {
        private final List<Entity> entities;
        private final GameEngine engine;
        private final ShopSystem shop;

        public CollisionSystem(GameEngine engine, List<Entity> entities, ShopSystem shop) {
                this.entities = entities;
                this.engine = engine;
                this.shop = shop;
        }

        @Override
        public void update(double dt) {
                if (shop != null && shop.getState().disableCollisions) return;

                List<Entity> toRemove = new ArrayList<>();

                // pairwise over ALL seeds (not restricted to same link)
                for (int i = 0; i < entities.size(); i++) {
                        Entity a = entities.get(i);
                        if (!a.has(Seed.class) || !a.has(Transform.class)) continue;
                        Seed sa = a.get(Seed.class);
                        Transform ta = a.get(Transform.class);

                        for (int j = i + 1; j < entities.size(); j++) {
                                Entity b = entities.get(j);
                                if (!b.has(Seed.class) || !b.has(Transform.class)) continue;
                                Seed sb = b.get(Seed.class);
                                Transform tb = b.get(Transform.class);

                                double dx = ta.x - tb.x, dy = ta.y - tb.y;
                                double d = Math.hypot(dx, dy);

                                // 12–14 px is a good approximate “touch” radius for our 12 px seeds
                                if (d < 14.0) {
                                        sa.collisions++;
                                        sb.collisions++;

                                        // separation impulse along the line joining the centers
                                        double push = (14.0 - d) * 0.2;
                                        if (d == 0) {
                                                sa.lateral +=  push;
                                                sb.lateral += -push;
                                        } else {
                                                double ux = dx / d;
                                                sa.lateral +=  ux * push;
                                                sb.lateral += -ux * push;
                                        }
                                }
                        }

                        // loss conditions
                        if (Math.abs(sa.lateral) > 24 || sa.collisions >= sa.capacity) {
                                sa.speed = -1; // sentinel so it won't move if it somehow survives one more tick
                                toRemove.add(a);
                                engine.incrementLost();
                                play.audio.AudioManager.getInstance().playSfx("/sfx/packetloss.wav");
                        }
                }

                // remove lost seeds
                entities.removeAll(toRemove);
        }
}
