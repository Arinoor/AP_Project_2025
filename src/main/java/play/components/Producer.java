package play.components;

/**
 * Producer component attached to a device (system).
 * Produces seeds at a fixed interval and (optionally) under per-type quotas.
 */
public class Producer {
        public double interval = 1.0;
        public double timer = 0.0;

        /** Remaining quotas per type. Use -1 for unlimited. */
        public int remainingSquare = -1;
        public int remainingTriangle = -1;

        public Producer(double interval) {
                this.interval = interval;
        }

        public Producer(double interval, int squareQuota, int triangleQuota) {
                this.interval = interval;
                this.remainingSquare = squareQuota;
                this.remainingTriangle = triangleQuota;
        }

        /** Returns true if at least one type still has quota or quotas are unlimited. */
        public boolean hasAnyQuota() {
                return (remainingSquare != 0) || (remainingTriangle != 0);
        }
}
