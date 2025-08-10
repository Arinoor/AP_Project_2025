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
        private int producedCount = 0;         // # spawned
        private int deliveredCount = 0;        // # delivered to reference (new)
        private int lostCount = 0;
        private int reachedReference = 0;
        private int coins = 0;

        public List<Entity> entities() { return entities; }

        public void addSystem(System sys) { systems.add(sys); }

        public void tick(double dt) {
                for (System s : systems) s.update(dt);
        }

        public void notifySeedDelivered(Seed seed) {
                deliveredCount++;
                // If you want to trigger listeners, do it here
        }

        public void incrementProduced() { producedCount++; }

        public void incrementLost() {
                lostCount++;
                AudioManager.getInstance().playSfx("/sfx/packetloss.wav");
        }

        public void incrementReachedReference() { reachedReference++; }

        public void incrementCoins(int amount) {
                coins += amount;
                if (coins < 0) coins = 0;
        }

        public int getCoins() { return coins; }
        public int getProducedCount() { return producedCount; }
        public int getDeliveredCount() { return deliveredCount; }  // new getter
        public int getLostCount() { return lostCount; }
        public int getReachedReference() { return reachedReference; }
}
