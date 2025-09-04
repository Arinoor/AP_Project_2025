package play.model.components;

public class Seed {
        public enum Type { SQUARE, TRIANGLE }

        public final Type type;

        // kinematics
        public double speed = 120.0;     // px/s
        public double accel = 0.0;       // px/s^2

        // LEGACY logical progress [0..1] (derived from arcPos / link.length)
        public double progress = 0.0;

        // lateral offset (px) relative to wire; large magnitude => loss
        public double lateral = 0.0;

        // impact flash energy (decays each tick); used for short "just collided" guard
        public double impactEnergy = 0.0;

        // collision budgeting (separate from noise)
        public int collisions = 0;
        public int capacity;

        // cumulative "noise" (in PORT UNITS). If noise > sizeUnits() => loss
        public double noise = 0.0;

        // the link this seed is currently traveling on
        public Link currentLink = null;

        public boolean justCollided = false;

        /** If true, this seed is travelling back along its currentLink toward the source. */
        public boolean returning = false;


        // Phase 2: precise arc-length state along the currentLink polyline
        public double arcPos = 0.0; // [0..currentLink.length]

        public Seed(Type type) {
                this.type = type;
                this.capacity = (type == Type.SQUARE) ? 3 : 4; // tolerant
        }

        /** Size in "port units" used for thresholds (square=2, triangle=3). */
        public double sizeUnits() {
                return (type == Type.SQUARE) ? 2.0 : 3.0;
        }
}
