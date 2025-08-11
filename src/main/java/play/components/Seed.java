package play.components;

public class Seed {
        public enum Type { SQUARE, TRIANGLE }

        public final Type type;

        // kinematics
        public double speed = 120.0;     // px/s
        public double accel = 0.0;       // px/s^2

        // progress along current link [0..1]
        public double progress = 0.0;

        // lateral offset (px) relative to wire; large magnitude => loss
        public double lateral = 0.0;

        // impact flash energy (decays each tick); used for short "just collided" guard
        public double impactEnergy = 0.0;

        // collision budgeting (separate from noise)
        public int collisions = 0;
        public int capacity;

        // cumulative "noise" (in PORT UNITS, not pixels). If noise > sizeUnits() => loss
        public double noise = 0.0;

        // the link this seed is currently traveling on
        public Link currentLink = null;

        public boolean justCollided = false;

        public Seed(Type type) {
                this.type = type;
                this.capacity = (type == Type.SQUARE) ? 3 : 4; // a bit more tolerant than before
        }

        /** Size in "port units" used for thresholds (square=2, triangle=3). */
        public double sizeUnits() {
                return (type == Type.SQUARE) ? 2.0 : 3.0;
        }
}
