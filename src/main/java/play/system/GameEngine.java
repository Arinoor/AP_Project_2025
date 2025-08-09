package play.system;

import play.core.Entity;
import play.events.DeliveryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GameEngine {

        private final List<Entity> entities = new ArrayList<>();
        private final List<Consumer<Double>> systems = new ArrayList<>();
        private final List<DeliveryListener> deliveryListeners = new ArrayList<>();

        // Game stats
        private int producedCount = 0;
        private int lostCount = 0;
        private int reachedReferenceCount = 0;

        // Wire accounting
        private double totalWire = 5000.0;
        private double remainingWire = 5000.0;

        // Optional tick callback
        private Consumer<Double> tickCallback;

        public List<Entity> entities() {
                return entities;
        }

        public void addSystem(Consumer<Double> system) {
                systems.add(system);
        }

        public void addDeliveryListener(DeliveryListener listener) {
                deliveryListeners.add(listener);
        }

        public void tick(double deltaTime) {
                for (Consumer<Double> system : systems) {
                        system.accept(deltaTime);
                }
                if (tickCallback != null) {
                        tickCallback.accept(deltaTime);
                }
        }

        public void setTickCallback(Consumer<Double> callback) {
                this.tickCallback = callback;
        }

        public void notifySeedDelivered(play.components.Seed seed) {
                producedCount++;
                for (DeliveryListener listener : deliveryListeners) {
                        listener.onSeedDelivered(seed);
                }
        }

        public void incrementLost() {
                lostCount++;
        }

        public void incrementProduced() {
                producedCount++;
        }

        public void incrementReachedReference() {
                reachedReferenceCount++;
        }

        public int producedCount() {
                return producedCount;
        }

        public int lostCount() {
                return lostCount;
        }

        public int reachedReferenceCount() {
                return reachedReferenceCount;
        }

        public void resetStats() {
                producedCount = 0;
                lostCount = 0;
                reachedReferenceCount = 0;
                remainingWire = totalWire;
        }

        // Wire accounting
        public double getTotalWire() {
                return totalWire;
        }

        public double getRemainingWire() {
                return remainingWire;
        }

        public boolean consumeWire(double length) {
                if (remainingWire >= length) {
                        remainingWire -= length;
                        return true;
                }
                return false;
        }

        public void addEntity(Entity e) {
                entities.add(e);
        }
}
