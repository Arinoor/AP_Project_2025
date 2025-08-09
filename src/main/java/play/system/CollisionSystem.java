package play.system;

import play.core.Entity;
import play.components.Seed;
import play.components.Transform;

import java.util.List;

/**
 * Very different collision approach: we only compare seeds that share the same Link entity.
 * When two seeds are close, they both increment collisions; nearby seeds get pushed laterally.
 */
public class CollisionSystem implements System {
        private final List<Entity> entities;
        public CollisionSystem(List<Entity> entities){ this.entities = entities; }
        @Override
        public void update(double dt) {
                // naive O(n^2) but our baseline has small counts (simple & readable)
                for (int i=0;i<entities.size();i++){
                        Entity a = entities.get(i);
                        if(!a.has(Seed.class) || !a.has(Transform.class)) continue;
                        Seed sa = a.get(Seed.class); Transform ta = a.get(Transform.class);
                        for(int j=i+1;j<entities.size();j++){
                                Entity b = entities.get(j);
                                if(!b.has(Seed.class) || !b.has(Transform.class)) continue;
                                Seed sb = b.get(Seed.class); Transform tb = b.get(Transform.class);
                                if(sa.currentLink==null || sb.currentLink==null) continue;
                                if(!sa.currentLink.equals(sb.currentLink)) continue;
                                double dx = ta.x - tb.x, dy = ta.y - tb.y;
                                double d = Math.hypot(dx, dy);
                                if(d < 12) { // collision threshold (different value)
                                        sa.collisions++; sb.collisions++;
                                        // push seeds laterally in opposite directions
                                        sa.lateral += (dx==0 && dy==0)? 1.0 : (dx/d)*2.0;
                                        sb.lateral += (dx==0 && dy==0)? -1.0 : (-dx/d)*2.0;
                                }
                        }
                        // if lateral exceeds threshold -> seed lost: mark by setting speed negative
                        if(Math.abs(sa.lateral) > 20) sa.speed = -1;
                        if(sa.collisions >= sa.capacity) sa.speed = -1;
                }
                // remove lost seeds (set speed <0)
                entities.removeIf(e -> e.has(Seed.class) && e.get(Seed.class).speed < 0);
        }
}
