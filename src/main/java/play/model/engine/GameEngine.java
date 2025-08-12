package play.model.engine;

import play.model.core.Entity;
import play.model.components.Seed;
import play.model.systems.System;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ECS-style engine used by your game.
 * Holds entities, systems, and global counters (coins, produced, delivered, lost, planned).
 */
public class GameEngine {

        // Entities & systems
        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems  = new ArrayList<>();

        // Global counters
        private int plannedTotal   = 0;
        private int producedCount  = 0;
        private int deliveredCount = 0;
        private int lostCount      = 0;
        private int coins          = 0;

        // --- ECS management ---
        public List<Entity> entities() {
                return entities;
        }

        public void addSystem(System s) {
                systems.add(s);
        }

        public void clearSystems() {
                systems.clear();
        }

        /** Advance the simulation by dt seconds. Calls systems in the order they were added. */
        public void tick(double dt) {
                // iterate on a snapshot to allow systems to add/remove other systems safely (rare)
                for (System s : new ArrayList<>(systems)) {
                        s.update(dt);
                }
        }

        /** Reset everything for a new level. */
        public void resetForLevel() {
                entities.clear();
                clearSystems();
                plannedTotal   = 0;
                producedCount  = 0;
                deliveredCount = 0;
                lostCount      = 0;
                coins          = 0;
        }

        // --- Game meta & scoring ---
        public void setPlannedTotal(int total) {
                this.plannedTotal = Math.max(0, total);
        }

        public int getPlannedTotal() {
                return plannedTotal;
        }

        public void incrementProduced() {
                producedCount++;
        }

        /** Called when a seed is delivered into a Reference system. */
        public void notifySeedDelivered(Seed s) {
                deliveredCount++;
                // coin award is handled elsewhere (QueueSystem on system entry), by design
        }

        public void incrementLost() {
                lostCount++;
        }

        public void incrementLostBy(int n) {
                if (n <= 0) return;
                lostCount += n;
        }

        public int getProducedCount() {
                return producedCount;
        }

        public int getDeliveredCount() {
                return deliveredCount;
        }

        public int getLostCount() {
                return lostCount;
        }

        // --- Currency ---
        public int getCoins() {
                return coins;
        }

        /** Can pass negative to spend; won’t go below zero. */
        public void incrementCoins(int delta) {
                coins += delta;
                if (coins < 0) coins = 0;
        }

        // --- Convenience (compatibility with existing code) ---
        /** Some code calls this; we map it to delivery for compatibility. */
        public void incrementReachedReference() {
                deliveredCount++;
        }
}
