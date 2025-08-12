package play.model.systems;

import play.model.core.Entity;
import play.model.components.Seed;
import play.model.engine.GameEngine;

/**
 * Owns temporary gameplay modifiers purchasable in the Shop.
 * UI must NOT mutate gameplay; it should only call these methods and show messages.
 */
public class ShopSystem implements System {

        // --- Costs (tweak/balance here)
        public static final int COST_ATAR     = 3; // disable impact waves 10s
        public static final int COST_AIRYAMAN = 4; // disable collisions 5s
        public static final int COST_ANAHITA  = 5; // reset packet noise immediately

        public static class ShopState {
                public boolean disableImpactWaves = false;
                public boolean disableCollisions  = false;
                public boolean disableLateral     = false;

                double impactOffUntil = 0.0;
                double collOffUntil   = 0.0;
                double latOffUntil    = 0.0;
        }

        private final GameEngine engine;
        private final ShopState state = new ShopState();
        private double time = 0.0;

        public ShopSystem(GameEngine engine) {
                this.engine = engine;
        }

        public ShopState getState() { return state; }

        @Override
        public void update(double dt) {
                time += dt;
                if (state.disableImpactWaves && time >= state.impactOffUntil) state.disableImpactWaves = false;
                if (state.disableCollisions  && time >= state.collOffUntil)   state.disableCollisions  = false;
                if (state.disableLateral     && time >= state.latOffUntil)    state.disableLateral     = false;
        }

        // === Purchases ===

        /** Disable impact waves for 10s. */
        public boolean buyAtar() {
                if (!deductCoins(COST_ATAR)) return false;
                state.disableImpactWaves = true;
                state.impactOffUntil = time + 10.0;
                return true;
        }

        /** Disable collisions for 5s. */
        public boolean buyAiryaman() {
                if (!deductCoins(COST_AIRYAMAN)) return false;
                state.disableCollisions = true;
                state.collOffUntil = time + 5.0;
                return true;
        }

        /** Immediately reset noise for all seeds (and related build-ups). */
        public boolean buyAnahita() {
                if (!deductCoins(COST_ANAHITA)) return false;

                // Sweep all seeds and clear noise + related transients
                for (Entity e : engine.entities()) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        s.noise = 0.0;        // <-- the key requirement

                }
                return true;
        }

        // === Helpers ===

        private boolean deductCoins(int cost) {
                if (engine.getCoins() < cost) return false;
                engine.incrementCoins(-cost);
                return true;
        }
}
