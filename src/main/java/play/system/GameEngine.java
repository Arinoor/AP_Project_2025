package play.system;

import play.audio.AudioManager;
import play.core.Entity;
import play.components.Seed;

import java.util.ArrayList;
import java.util.List;

public class GameEngine {
        private final List<Entity> entities = new ArrayList<>();
        private final List<System> systems = new ArrayList<>();

        // counters / HUD
        private int producedCount = 0;
        private int deliveredCount = 0;
        private int lostCount = 0;
        private int reachedReference = 0;
        private int coins = 0;

        // level meta
        private int plannedTotal = 0;

        public List<Entity> entities() { return entities; }
        public void addSystem(System sys) { systems.add(sys); }

        public void tick(double dt) {
                for (System s : systems) s.update(dt);
        }

        // --- Level lifecycle ---
        public void resetForLevel() {
                systems.clear();
                entities.clear();
                producedCount = 0;
                deliveredCount = 0;
                lostCount = 0;
                reachedReference = 0;
                coins = 0;
                plannedTotal = 0;
        }

        public void setPlannedTotal(int planned) {
                this.plannedTotal = Math.max(0, planned);
        }
        public int getPlannedTotal() { return plannedTotal; }

        // --- Counters ---
        public void notifySeedDelivered(Seed seed) {
                deliveredCount++;
                // (coins on delivery, if any, are handled elsewhere)
        }

        public void incrementProduced() { producedCount++; }

        public void incrementLost() {
                lostCount++;
                AudioManager.getInstance().playSfx("/sfx/packetloss.wav");
        }

        public void incrementLostBy(int amount) {
                if (amount <= 0) return;
                lostCount += amount;
                // play one sfx ping to avoid spam on bulk loss
                AudioManager.getInstance().playSfx("/sfx/packetloss.wav");
        }

        public void incrementReachedReference() { reachedReference++; }

        public void incrementCoins(int amount) {
                coins += amount;
                if (coins < 0) coins = 0;
        }

        // --- Getters ---
        public int getCoins() { return coins; }
        public int getProducedCount() { return producedCount; }
        public int getDeliveredCount() { return deliveredCount; }
        public int getLostCount() { return lostCount; }
        public int getReachedReference() { return reachedReference; }
}
