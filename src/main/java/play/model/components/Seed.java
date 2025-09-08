package play.model.components;

import play.model.components.Link;

/** Packet (seed) travelling in the network. */
public class Seed {
        public enum Type { SQUARE, TRIANGLE, INFINITE, SECURE }

        public final Type type;

        // kinematics
        public double speed = 120.0;     // px/s
        public double accel = 0.0;       // px/s^2
        /** Second acceleration (jerk), affects accel each frame: accel += jerk * dt. */
        public double jerk  = 0.0;       // px/s^3

        // legacy normalized progress [0..1] (derived from arc length / link length)
        public double progress = 0.0;

        // lateral offset (px) relative to wire; large magnitude => loss
        public double lateral = 0.0;

        // brief impact flash energy (also used as a short post-collision guard)
        public double impactEnergy = 0.0;

        // collision budgeting (separate from noise)
        public int collisions;
        public int capacity;

        // cumulative "noise" (in PORT UNITS). If noise > sizeUnits() => loss
        public double noise = 0.0;

        // the link this seed is currently traveling on (null when queued/inside a system)
        public Link currentLink = null;

        public boolean justCollided = false;

        /** If true, this seed is travelling back along its currentLink toward the source. */
        public boolean returning = false;

        // Phase 2: precise arc-length state along the currentLink polyline
        public double arcPos = 0.0; // [0..currentLink.length]

        public Seed(Type type) {
                this.type = type;
                // Keep capacities: TRIANGLE=4, others=3
                this.capacity = (type == Type.TRIANGLE) ? 4 : 3;
        }

        /**
         * Size in "port units" used for thresholds.
         * square=2, triangle=3, infinite=1, secure=4
         */
        public double sizeUnits() {
                switch (type) {
                        case SQUARE:   return 2.0;
                        case TRIANGLE: return 3.0;
                        case INFINITE: return 1.0;
                        case SECURE:   return 4.0;
                        default:       return 2.0;
                }
        }
}
