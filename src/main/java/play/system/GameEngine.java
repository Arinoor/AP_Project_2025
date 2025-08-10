package play.system;

import play.core.Entity;
import play.events.DeliveryListener;
import play.components.Seed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Central engine: stores entities, systems and stats.
 * Added: createEntity(), setTotalWire(), fireDeliveredEvent(Seed) (alias), notifySeedDelivered(Seed).
 */
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

        /**
         * Convenience factory so callers don't directly new Entity() when engine should own it.
         */
        public play.core.Entity createEntity() {
                play.core.Entity e = new play.core.Entity();
                this.entities.add(e);
                return e;
        }

        /**
         * Backwards-compat alias used in some code paths.
         */
        public void addEntity(Entity e) {
                if (!entities.contains(e)) entities.add(e);
        }

        /**
         * Notify listeners that a seed was delivered.
         */
        public void notifySeedDelivered(play.components.Seed seed) {
                // producedCount++;  // <-- remove this line
                for (DeliveryListener listener : deliveryListeners) {
                        listener.onSeedDelivered(seed);
                }
        }

        /**
         * Alias for older callsites.
         */
        public void fireDeliveredEvent(Seed seed) {
                notifySeedDelivered(seed);
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

        /**
         * Attempt to consume wire. Returns true if enough remaining.
         */
        public boolean consumeWire(double length) {
                if (remainingWire >= length) {
                        remainingWire -= length;
                        return true;
                }
                return false;
        }

        /**
         * Set total wire (e.g. loaded from level). Also resets remainingWire to that value.
         */
        public void setTotalWire(double total) {
                this.totalWire = total;
                this.remainingWire = total;
        }
}
