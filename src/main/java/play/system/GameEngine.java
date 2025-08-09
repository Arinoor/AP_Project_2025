package play.system;

import play.core.Entity;
import play.events.DeliveryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Engine: holds entities and systems; also collects statistics for end conditions and provides event hooks.
 */
public class GameEngine {
        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems = new ArrayList<>();
        private final CopyOnWriteArrayList<DeliveryListener> deliveryListeners = new CopyOnWriteArrayList<>();
        private Consumer<Double> onTick;

        // stats
        private int producedCount = 0;
        private int lostCount = 0;
        private int reachedReferenceCount = 0;

        public Entity createEntity() { Entity e = new Entity(); entities.add(e); return e; }
        public List<Entity> entities() { return entities; }
        public void addSystem(System s) { systems.add(s); }
        public void setTickCallback(Consumer<Double> cb) { this.onTick = cb; }

        public void tick(double dt) {
                for (System s : systems) {
                        s.update(dt);
                }
                if (onTick != null) onTick.accept(dt);
        }

        // Delivery listeners
        public void addDeliveryListener(DeliveryListener l) { deliveryListeners.addIfAbsent(l); }
        public void removeDeliveryListener(DeliveryListener l) { deliveryListeners.remove(l); }

        public void fireDeliveredEvent(play.components.Seed seed) {
                for (DeliveryListener l : deliveryListeners) l.onSeedDelivered(seed);
        }

        // Stats API
        public void incrementProduced() { producedCount++; }
        public void incrementLost() { lostCount++; }
        public void incrementReachedReference() { reachedReferenceCount++; }

        public int producedCount() { return producedCount; }
        public int lostCount() { return lostCount; }
        public int reachedReferenceCount() { return reachedReferenceCount; }

        public void resetStats() { producedCount = 0; lostCount = 0; reachedReferenceCount = 0; }
}
