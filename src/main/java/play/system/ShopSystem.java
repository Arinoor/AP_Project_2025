package play.system;

import play.core.Entity;
import play.components.Seed;

import java.util.List;

/**
 * Manages shop effects (Atar, Airyaman, Anahita).
 * Effects are applied by toggling flags and timers; other systems read those flags via ShopState.
 */
public class ShopSystem implements System {

        public static class ShopState {
                public boolean disableLateral = false;
                public double disableLateralUntil = 0;
                public boolean disableCollisions = false;
                public double disableCollisionsUntil = 0;
        }

        private final ShopState state = new ShopState();
        private final List<Entity> entities;
        private double gameTime = 0;

        public ShopSystem(List<Entity> engineEntities){
                this.entities = engineEntities;
        }

        @Override
        public void update(double dt) {
                gameTime += dt;
                if(state.disableLateral && gameTime >= state.disableLateralUntil) state.disableLateral = false;
                if(state.disableCollisions && gameTime >= state.disableCollisionsUntil) state.disableCollisions = false;
        }

        // API for shop purchases
        public boolean purchaseAtar(int coins, double now){
                // Atar: disable lateral drift (impact) for 10s
                if(coins < 3) return false;
                state.disableLateral = true;
                state.disableLateralUntil = now + 10.0;
                return true;
        }
        public boolean purchaseAiryaman(int coins, double now){
                // Airyaman: disable collisions for 5s
                if(coins < 4) return false;
                state.disableCollisions = true;
                state.disableCollisionsUntil = now + 5.0;
                return true;
        }
        public boolean purchaseAnahita(int coins){
                // Anahita: reset collision counters on active seeds
                if(coins < 5) return false;
                for(Entity e : entities){
                        if(e.has(Seed.class)) {
                                e.get(Seed.class).collisions = 0;
                                e.get(Seed.class).lateral = 0;
                        }
                }
                return true;
        }

        public ShopState getState(){ return state; }
}
