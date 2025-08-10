package play.components;

public class Producer {
        public double interval; // seconds
        public double timer = 0.0;

        public Producer(double interval) {
                this.interval = Math.max(0.01, interval);
        }
}
