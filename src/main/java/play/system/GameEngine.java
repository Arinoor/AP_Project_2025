package play.system;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import play.core.Entity;
import play.components.Seed;

/**
 * Minimal game engine shell used by systems.
 * - Holds world entities
 * - Schedules update consumers
 * - Tracks counters (coins, lost, reachedReference)
 * - Fires delivery listeners on seed delivery
 */
public class GameEngine {

        // World
        private final List<Entity> world = new ArrayList<>();

        // Update pipeline
        private final List<Consumer<Double>> systems = new ArrayList<>();

        // Delivery listeners
        public interface DeliveryListener {
                void onSeedDelivered(Seed seed);
        }
        private final List<DeliveryListener> deliveryListeners = new ArrayList<>();

        // Counters / stats
        private int producedCount = 0;           // number of notifySeedDelivered calls
        private int lostCount = 0;
        private int reachedReferenceCount = 0;
        private int coins = 0;

        public GameEngine() {
                // default coin rule: +1 square, +2 triangle
                addDeliveryListener(seed -> {
                        coins += (seed.type == Seed.Type.SQUARE) ? 1 : 2;
                });
        }

        /* ===== World & Systems ===== */
        public List<Entity> entities() { return world; }

        public void addEntity(Entity e) { world.add(e); }

        public void addSystem(Consumer<Double> sys) { systems.add(sys); }

        /** Minimal run loop placeholder; replace with your window/game loop as needed. */
        public void run() {
                // no-op to avoid blocking; integrate with your app's main loop elsewhere.
        }

        /* ===== Delivery / Stats ===== */
        public void addDeliveryListener(DeliveryListener l) { deliveryListeners.add(l); }

        public void notifySeedDelivered(Seed seed) {
                producedCount++;
                for (DeliveryListener l : deliveryListeners) {
                        l.onSeedDelivered(seed);
                }
        }

        public void incrementLost() { lostCount++; }

        public void incrementReachedReference() { reachedReferenceCount++; }

        public int getCoins() { return coins; }
        public int getProducedCount() { return producedCount; }
        public int getLostCount() { return lostCount; }
        public int getReachedReferenceCount() { return reachedReferenceCount; }

        /* ===== Tick utilities (optional) ===== */
        public void update(double dt) {
                for (Consumer<Double> sys : systems) sys.accept(dt);
        }
}
