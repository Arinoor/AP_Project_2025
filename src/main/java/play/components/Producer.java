package play.components;

/**
 * Producer component:
 * - interval: how often this system tries to emit seeds
 * - remainingSquare / remainingTriangle: fixed per-level quotas
 *   When either reaches 0, that type is no longer produced.
 */
public class Producer {
        public double interval;
        public double timer = 0.0;

        /** Remaining seeds to spawn for this producer (per type). */
        public int remainingSquare = 0;
        public int remainingTriangle = 0;

        public Producer(double interval) {
                this.interval = interval;
        }

        public boolean hasAnyRemaining() {
                return (remainingSquare > 0) || (remainingTriangle > 0);
        }
}
