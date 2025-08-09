package play.system;

import play.core.Entity;
import play.components.Seed;
import play.components.Transform;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairwise collision detection on same link. Increases collisions and lateral drift.
 * When a seed becomes lost, it marks engine.incrementLost() so packetLoss increments.
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

                                if (sa.currentLink == null || sb.currentLink == null) continue;
                                if (!sa.currentLink.equals(sb.currentLink)) continue;

                                double dx = ta.x - tb.x, dy = ta.y - tb.y;
                                double d = Math.hypot(dx, dy);
                                if (d < 14.0) {
                                        sa.collisions++; sb.collisions++;
                                        double push = (14.0 - d) * 0.2;
                                        sa.lateral += (dx == 0 && dy == 0) ? push : (dx / d) * push;
                                        sb.lateral += (dx == 0 && dy == 0) ? -push : (-dx / d) * push;
                                }
                        }

                        // loss conditions
                        if (Math.abs(sa.lateral) > 24 || sa.collisions >= sa.capacity) {
                                sa.speed = -1;
                                toRemove.add(a);
                                engine.incrementLost();
                                // sfx call via AudioManager (keep non-blocking)
                                play.audio.AudioManager.getInstance().playSfx("sfx/packetloss.wav");
                        }
                }

                // remove lost seeds
                entities.removeAll(toRemove);
        }
}
