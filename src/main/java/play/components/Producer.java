package play.components;

import play.core.Component;

/** Mark an OUTPUT port as a producer with a spawn interval in seconds. */
public class Producer implements Component {
        public double intervalSec;
        public double timer = 0.0;
        public Producer(double intervalSec) { this.intervalSec = Math.max(0.05, intervalSec); }
}
