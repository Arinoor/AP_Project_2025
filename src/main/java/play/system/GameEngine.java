package play.system;

import play.core.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Simple engine that stores entities and systems and runs the tick loop */
public class GameEngine {
        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems = new ArrayList<>();
        private Consumer<Double> onTick; // for UI to repaint

        public Entity createEntity(){ Entity e = new Entity(); entities.add(e); return e; }
        public List<Entity> entities(){ return entities; }
        public void addSystem(System s){ systems.add(s); }
        public void setTickCallback(Consumer<Double> cb){ this.onTick = cb; }

        public void tick(double dt){
                for(System s : systems) s.update(dt);
                if(onTick!=null) onTick.accept(dt);
        }
}
