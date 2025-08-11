package play.components;

public class Seed {
        public enum Type { SQUARE, TRIANGLE }

        public final Type type;

        // world position
        public double px = 0.0;
        public double py = 0.0;

        // kinematics (world space)
        public double vx = 0.0;
        public double vy = 0.0;
        public double ax = 0.0;
        public double ay = 0.0;

        // compatibility bookkeeping (kept for HUD / rules)
        public double impactEnergy = 0.0;  // used as a brief collision guard
        public boolean justCollided = false;

        // collision budgeting per doc
        public int collisions = 0;
        public int capacity;

        // progress on current link is derived each frame via projection, but we keep it for UI
        public double progress = 0.0;

        // still useful to expose the instantaneous perpendicular offset magnitude
        public double lateral = 0.0;

        // paired link (component, not entity)
        public Link currentLink = null;

        public Seed(Type type) {
                this.type = type;
                this.capacity = (type == Type.SQUARE) ? 2 : 3;
        }
}
