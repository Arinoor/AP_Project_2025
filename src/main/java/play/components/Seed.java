package play.components;

public class Seed {
        public enum Type { SQUARE, TRIANGLE }

        public final Type type;

        // kinematics
        public double speed = 120.0;     // px/s along the link
        public double accel = 0.0;       // px/s^2 along the link

        // where the seed is on its current link: 0..1
        public double progress = 0.0;

        // lateral offset from the wire in pixels (visual & collision space)
        public double lateral = 0.0;

        // impact wave energy to be decayed each tick (AoE ripple cooldown)
        public double impactEnergy = 0.0;

        // collision budgeting per project doc
        public int collisions = 0;
        public int capacity;

        // pairing with the link (component, not entity)
        public Link currentLink = null;

        // guards immediate re-collisions
        public boolean justCollided = false;

        public Seed(Type type) {
                this.type = type;
                // Slightly more tolerant than before so “one bump” isn’t death.
                this.capacity = (type == Type.SQUARE) ? 3 : 5;
        }
}
