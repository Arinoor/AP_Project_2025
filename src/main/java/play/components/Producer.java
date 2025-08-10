package play.components;

import play.core.Component;

/** Spawns seeds from this PORT entity at a fixed interval. */
public class Producer implements Component {
        /** Seconds between spawns. */
        public final double intervalSec;
        /** Internal timer; systems may mutate. */
        public double timer = 0.0;

        public Producer(double intervalSec) {
                this.intervalSec = Math.max(0.05, intervalSec);
        }
}
