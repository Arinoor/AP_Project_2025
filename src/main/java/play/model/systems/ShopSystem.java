package play.model.systems;

import play.model.engine.GameEngine;
import play.model.core.Entity;
import play.model.components.Seed;
import play.model.constants.GameBalance;

public class ShopSystem implements System {
        public static class ShopState {
                public boolean disableImpactWaves = false;
                public boolean disableCollisions  = false;
                public boolean disableLateral     = false;
                public boolean aergiaSelectionActive = false;
                public boolean sisyphusSelectionActive = false;

                double impactOffUntil = 0.0;
                double collOffUntil   = 0.0;
                double latOffUntil    = 0.0;
                public double aergiaCooldown = 0.0;
                public double sisyphusCooldown = 0.0;
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

                if (state.aergiaCooldown > 0) {
                        state.aergiaCooldown -= dt;
                }
                if (state.sisyphusCooldown > 0) {
                        state.sisyphusCooldown -= dt;
                }
        }

        public boolean buyAtar() {
                if (engine.getCoins() < GameBalance.COST_ATAR) return false;
                engine.incrementCoins(-GameBalance.COST_ATAR);
                state.disableImpactWaves = true;
                state.impactOffUntil = time + 10.0;
                return true;
        }

        public boolean buyAiryaman() {
                if (engine.getCoins() < GameBalance.COST_AIRYAMAN) return false;
                engine.incrementCoins(-GameBalance.COST_AIRYAMAN);
                state.disableCollisions = true;
                state.collOffUntil = time + 5.0;
                return true;
        }

        /** Reset noise/collisions now (model-side, not UI). */
        public boolean buyAnahita() {
                if (engine.getCoins() < GameBalance.COST_ANAHITA) return false;
                engine.incrementCoins(-GameBalance.COST_ANAHITA);
                for (Entity e : engine.entities()) {
                        if (!e.has(Seed.class)) continue;
                        Seed s = e.get(Seed.class);
                        s.collisions = 0;
                        s.noise = 0.0;
                        s.lateral = 0.0;
                }
                return true;
        }

        public boolean buyAergia() {
                if (engine.getCoins() < GameBalance.COST_AERGIA || state.aergiaCooldown > 0) {
                        return false;
                }
                state.aergiaSelectionActive = true;
                return true;
        }

        public boolean buySisyphus() {
                if (engine.getCoins() < GameBalance.COST_SISYPHUS || state.sisyphusCooldown > 0) {
                        return false;
                }
                state.sisyphusSelectionActive = true;
                engine.incrementCoins(-GameBalance.COST_SISYPHUS);
                state.sisyphusCooldown = 1.0; // 30 seconds cooldown
                return true;
        }

}
