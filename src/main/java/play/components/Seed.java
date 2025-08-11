package play.components;

public class Seed {
        public enum Type { SQUARE, TRIANGLE }

        public final Type type;

        // kinematics
        public double speed = 120.0;     // units / s
        public double accel = 0.0;       // units / s^2

        // where the seed is on its current link: 0..1
        public double progress = 0.0;

        // lateral offset from the wire (impact causes drift). "Death" if exceeds threshold
        public double lateral = 0.0;

        // impact wave energy to be decayed each tick (AoE ripple)
        public double impactEnergy = 0.0;

        // collision budgeting per project doc
        public int collisions = 0;
        public int capacity;

        // pairing with the link (component, not entity)
        public Link currentLink = null;

        // short guard to avoid instant re-collisions
        public boolean justCollided = false;

        public Seed(Type type) {
                this.type = type;
                // More tolerant: no loss on 1–2 hits. Keep triangle > square.
                this.capacity = (type == Type.SQUARE) ? 4 : 6;
        }
}
