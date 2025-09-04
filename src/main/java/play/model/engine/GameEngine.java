package play.model.engine;

import play.model.core.Entity;
import play.model.events.*;
import play.model.systems.System;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameEngine {

        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems  = new ArrayList<>();

        // Scores / meta
        private int producedCount = 0;
        private int deliveredCount = 0;
        private int lostCount = 0;
        private int coins = 0;
        private int plannedTotal = 0;

        // Event bus for model events
        private final EventBus eventBus = new EventBus();

        public GameEngine() {
                coins = 7000;
        }

        // --- Entities / Systems ---
        public List<Entity> entities() { return entities; }

        public void addSystem(System s) { systems.add(s); }
        public void clearSystems()      { systems.clear(); }

        public void tick(double dt) {
                // iterate over a copy in case systems mutate the list
                List<System> snapshot = new ArrayList<>(systems);
                for (System s : snapshot) s.update(dt);
        }

        // --- Meta / counters (kept for compatibility) ---
        public void setPlannedTotal(int planned) { this.plannedTotal = Math.max(0, planned); }
        public int getPlannedTotal()             { return plannedTotal; }

        public int getProducedCount()  { return producedCount; }
        public int getDeliveredCount() { return deliveredCount; }
        public int getLostCount()      { return lostCount; }
        public int getCoins()          { return coins; }

        // --- Mutations also post events ---
        public void incrementProduced() {
                producedCount++;
                eventBus.post(new ProducedEvent(producedCount));
        }

        public void incrementLost() {
                lostCount++;
                eventBus.post(new SeedLostEvent(1, lostCount));
        }

        public void incrementLostBy(int n) {
                if (n <= 0) return;
                lostCount += n;
                eventBus.post(new SeedLostEvent(n, lostCount));
        }

        public void incrementCoins(int delta) {
                if (delta == 0) return;
                coins += delta;
                if (coins < 0) coins = 0;
                eventBus.post(new CoinsChangedEvent(delta, coins));
        }

        public void incrementReachedReference() {
                deliveredCount++;
                // event posted in notifySeedDelivered to carry type info
        }

        /** Legacy hook: called when a seed is delivered; we emit a typed event here. */
        public void notifySeedDelivered(play.model.components.Seed s) {
                // ensure delivered counter is already bumped by caller (QueueSystem does this)
                eventBus.post(new SeedDeliveredEvent(s.type, deliveredCount));
        }

        /** Clears counters and entities; use when reloading a level with the same engine. */
        public void resetForLevel() {
                entities.clear();
                systems.clear();
                producedCount = 0;
                deliveredCount = 0;
                lostCount = 0;
                coins = 0;
                plannedTotal = 0;
        }

        public EventBus events() { return eventBus; }

        // Optional read-only views if you need them
        public List<System> systemsView() { return Collections.unmodifiableList(systems); }
}
