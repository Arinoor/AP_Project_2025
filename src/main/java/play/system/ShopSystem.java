package play.system;

import play.components.Seed;
import play.core.Entity;

public class ShopSystem implements System {
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

        // shop actions per project doc
        public boolean buyAtar() { // disable Impact waves 10s, cost 3
                if (engine.getCoins() < 3) return false;
                engine.incrementCoins(-3);
                state.disableImpactWaves = true;
                state.impactOffUntil = time + 10.0;
                return true;
        }

        public boolean buyAiryaman() { // disable collisions 5s, cost 4
                if (engine.getCoins() < 4) return false;
                engine.incrementCoins(-4);
                state.disableCollisions = true;
                state.collOffUntil = time + 5.0;
                return true;
        }

        public boolean buyAnahita() { // reset packet "noise" now, cost 5
                if (engine.getCoins() < 5) return false;
                engine.incrementCoins(-5);

                // Immediately sweep all seeds and clear noise (and related build-ups)
                for (Entity e : engine.entities()) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        s.noise = 0.0;         // <-- the important part
                }
                return true;
        }
}
