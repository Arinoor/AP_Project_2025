package play.system;

import play.core.Entity;
import play.events.DeliveryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameEngine {
        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems = new ArrayList<>();
        private final CopyOnWriteArrayList<DeliveryListener> deliveryListeners = new CopyOnWriteArrayList<>();
        private Consumer<Double> onTick;

        // stats
        private int producedCount = 0;
        private int lostCount = 0;
        private int reachedReferenceCount = 0;

        // wire accounting
        private double totalWire = 5000.0;
        private double remainingWire = 5000.0;

        public Entity createEntity() { Entity e = new Entity(); entities.add(e); return e; }
        public List<Entity> entities() { return entities; }
        public void addSystem(System s) { systems.add(s); }
        public void setTickCallback(Consumer<Double> cb) { this.onTick = cb; }

        public void tick(double dt) {
                for (System s : systems) s.update(dt);
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

        // Wire accounting
        public void setTotalWire(double total) {
                if (total < 0) total = 0;
                this.totalWire = total;
                this.remainingWire = total;
        }
        public double getTotalWire() { return totalWire; }
        public double getRemainingWire() { return remainingWire; }
        /**
         * Consume `length` of wire. Returns true if enough wire existed and consumption succeeded.
         */
        public boolean consumeWire(double length) {
                if (length < 0) return false;
                if (length <= remainingWire) {
                        remainingWire -= length;
                        return true;
                }
                return false;
        }
        public void refundWire(double length) {
                if (length < 0) return;
                remainingWire += length;
                if (remainingWire > totalWire) remainingWire = totalWire;
        }
}
