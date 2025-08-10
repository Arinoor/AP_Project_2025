package play.system;

import java.util.List;

import play.core.Entity;
import play.components.Seed;

/**
 * Shop flags & timers. Other systems read State to gate behavior.
 * Atar  -> disableImpactWaves (10s)
 * Airyaman -> disableCollisions (5s)
 * Anahita -> reset all seed "noise" (collision count) once
 */
public class ShopSystem {

        private final GameEngine engine;

        public static class State {
                public boolean disableCollisions = false;
                public boolean disableLateral    = false;
                public boolean disableImpactWaves = false; // <-- needed by CollisionSystem
        }

        private final State state = new State();

        private double impactOffTimer   = 0.0;
        private double collideOffTimer  = 0.0;
        private boolean anahitaRequested = false;

        public ShopSystem(GameEngine engine) {
                this.engine = engine;
        }

        public State getState() {
                return state;
        }

        /** Call when user buys "O' Atar". */
        public void activateAtar(double seconds) {
                state.disableImpactWaves = true;
                impactOffTimer = Math.max(impactOffTimer, seconds);
        }

        /** Call when user buys "O’ Airyaman". */
        public void activateAiryaman(double seconds) {
                state.disableCollisions = true;
                collideOffTimer = Math.max(collideOffTimer, seconds);
        }

        /** Call when user buys "O' Anahita". */
        public void activateAnahita() {
                anahitaRequested = true;
        }

        /** Advance timers & apply one-shot effects. */
        public void update(double dt) {
                // timers
                if (state.disableImpactWaves) {
                        impactOffTimer -= dt;
                        if (impactOffTimer <= 0) {
                                impactOffTimer = 0;
                                state.disableImpactWaves = false;
                        }
                }
                if (state.disableCollisions) {
                        collideOffTimer -= dt;
                        if (collideOffTimer <= 0) {
                                collideOffTimer = 0;
                                state.disableCollisions = false;
                        }
                }

                // one-shot: reset noise/collisions on all active seeds
                if (anahitaRequested) {
                        List<Entity> all = engine.entities();
                        for (Entity e : all) {
                                if (e.has(Seed.class)) {
                                        e.get(Seed.class).collisions = 0;
                                }
                        }
                        anahitaRequested = false;
                }
        }
}
