package play.system;

import play.core.Entity;
import play.components.Link;
import play.components.Seed;
import play.components.Transform;

import java.util.List;

/**
 * Moves seeds along their Link. When progress >=1.0, it clears seed.currentLink (arrival)
 * incompatible seeds accelerate slightly (different rule than previous implementations).
 */
public class SeedMovementSystem implements System {
        private final List<Entity> entities;
        public SeedMovementSystem(List<Entity> entities){ this.entities = entities; }

        @Override
        public void update(double dt) {
                for(Entity e : entities){
                        if(!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);
                        if(s.currentLink == null) continue;

                        Link link = s.currentLink.get(Link.class);
                        if(link==null) continue;

                        // acceleration for incompatible type: if seed type != fromPort's port shape (quick check)
                        boolean incompatible = false;
                        if(link.fromPort!=null && link.fromPort.has(play.components.PortInfo.class)){
                                Seed.Type stype = (s.type==Seed.Type.SQUARE)?Seed.Type.SQUARE:Seed.Type.TRIANGLE;
                                // we keep it simple: incompatible => faster (different behaviour)
                                incompatible = (link.fromPort.get(play.components.PortInfo.class).shape.toString()
                                        .equalsIgnoreCase("SQUARE") && s.type==Seed.Type.TRIANGLE)
                                        || (link.fromPort.get(play.components.PortInfo.class).shape.toString()
                                        .equalsIgnoreCase("TRIANGLE") && s.type==Seed.Type.SQUARE);
                        }
                        double sp = s.speed + (incompatible? 0.05 : 0);
                        s.progress += sp * dt;
                        // move transform along link line
                        Transform a = link.fromPort.get(Transform.class);
                        Transform b = link.toPort.get(Transform.class);
                        double vx = b.x - a.x, vy = b.y - a.y;
                        double px = a.x + vx * Math.min(1, s.progress);
                        double py = a.y + vy * Math.min(1, s.progress);
                        // apply lateral drift perpendicular to link
                        double len = Math.hypot(vx, vy);
                        if(len > 1e-6){
                                double nx = -vy/len, ny = vx/len;
                                px += nx * s.lateral;
                                py += ny * s.lateral;
                        }
                        t.x = px; t.y = py;
                        // if arrived
                        if(s.progress >= 1.0) {
                                s.currentLink = null;
                                s.progress = 0;
                                s.lateral = 0;
                        }
                }
        }
}
