package play.components;

import play.core.Component;

/**
 * Seed (packet) data.
 * - Triangles may accelerate on incompatible starts (see ProductionSystem).
 * - Collisions & lateral are used by CollisionSystem / impact AoE.
 */
public class Seed implements Component {

        public enum Type { SQUARE, TRIANGLE }

        public final Type type;

        /** Current link the seed is traversing (null means it's inside a node / detached / done). */
        public Link currentLink = null;

        /** Parametric progress along currentLink in [0..1]. */
        public double progress = 0.0;

        /** Forward speed along the link (units/sec). */
        public double speed = 0.0;

        /** Optional forward acceleration (used for TRIANGLE on incompatible starts). */
        public double accel = 0.0;

        /** Lateral displacement (impact wave pushes modify this). */
        public double lateral = 0.0;

        /** How many collisions this seed has suffered. */
        public int collisions = 0;

        /** Max allowed collisions before loss (doc: 2 for square, 3 for triangle). */
        public int capacity;

        /** Convenience alive flag for systems that want to early-out. */
        public boolean alive = true;

        public Seed(Type type) {
                this.type = type;
                this.capacity = (type == Type.SQUARE) ? 2 : 3;
        }
}
