package play.model.components;

/**
 * Producer component attached to a device (system).
 * Produces seeds at a fixed interval and under per-type quotas. Use -1 for unlimited.
 */
public class Producer {
        public double interval = 1.0;
        public double timer = 0.0;

        /** Remaining quotas per type. Use -1 for unlimited. */
        public int remainingSquare   = 0;
        public int remainingTriangle = 0;
        public int remainingInfinite = 0;
        public int remainingSecure   = 0;
        public int remainingHeavy   = 0;

        public Producer(double interval) {
                this.interval = interval;
        }

        /** Full ctor with all packet types. */
        public Producer(double interval, int squareQuota, int triangleQuota, int infiniteQuota, int secureQuota, int heavyQuota) {
                this.interval = interval;
                this.remainingSquare = squareQuota;
                this.remainingTriangle = triangleQuota;
                this.remainingInfinite = infiniteQuota;
                this.remainingSecure   = secureQuota;
                this.remainingHeavy = heavyQuota;
        }

        /** Returns true if at least one type still has quota or quotas are unlimited. */
        public boolean hasAnyQuota() {
                return (remainingSquare != 0) || (remainingTriangle != 0) || (remainingInfinite != 0) || (remainingSecure != 0) || (remainingHeavy != 0);
        }
}

