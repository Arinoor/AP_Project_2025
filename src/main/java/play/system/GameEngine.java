package play.system;

import play.core.Entity;
import play.events.DeliveryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Holds entities & systems and drives update loop. */
public class GameEngine {
        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems = new ArrayList<>();
        private Consumer<Double> onTick;
        private final CopyOnWriteArrayList<DeliveryListener> deliveryListeners = new CopyOnWriteArrayList<>();


        public Entity createEntity(){ Entity e = new Entity(); entities.add(e); return e; }
        public List<Entity> entities(){ return entities; }
        public void addSystem(System s){ systems.add(s); }
        public void setTickCallback(Consumer<Double> cb){ this.onTick = cb; }
        public void addDeliveryListener(DeliveryListener l) {
                deliveryListeners.addIfAbsent(l);
        }
        public void removeDeliveryListener(DeliveryListener l) {
                deliveryListeners.remove(l);
        }
        public void fireDeliveredEvent(play.components.Seed seed) {
                for (DeliveryListener l : deliveryListeners) l.onSeedDelivered(seed);
        }

        public void tick(double dt){
                for(System s : systems) s.update(dt);
                if(onTick!=null) onTick.accept(dt);
        }
}
