package play.system;

import play.core.Entity;
import play.components.Seed;
import play.components.Transform;

import java.util.List;

/**
 * Spawns seeds at "source" port-entities annotated with
 * a small 'producer' convention: a Transform.x < 200 used here for demo.
 * In real extension: add explicit Producer component.
 */
public class ProductionSystem implements System {
        private final List<Entity> entities;
        private double timer = 0;
        private final double interval = 1.0; // seconds

        public ProductionSystem(List<Entity> entities){ this.entities = entities; }

        @Override
        public void update(double dt) {
                timer += dt;
                if(timer < interval) return;
                timer = 0;
                // spawn a seed at reasonable source transform
                for(Entity e : entities){
                        if(e.has(Transform.class) && e.has(play.components.PortInfo.class)){
                                Transform t = e.get(Transform.class);
                                play.components.PortInfo p = e.get(play.components.PortInfo.class);
                                if(p.io == play.components.PortInfo.IO.OUT && t.x < 180){
                                        Entity seed = new Entity();
                                        seed.add(new Transform(t.x, t.y));
                                        seed.add(new Seed(Seed.Type.SQUARE));
                                        // put newly created entity into engine list by reflection:
                                        // we expect engine.entities() passed to systems to be mutable.
                                        entities.add(seed);
                                        break; // spawn only one per interval in demo
                                }
                        }
                }
        }
}
