package play.components;

import play.core.Component;
import play.core.Entity;

/**
 * A conduit between two port entities.
 * Systems are free to precompute/maintain length & unit direction.
 */
public class Link implements Component {

        /** OUT port entity (source). */
        public final Entity fromPort;

        /** IN port entity (destination). */
        public final Entity toPort;

        /** Geometric length of the segment in world units. */
        public double length;

        /** Unit direction from fromPort -> toPort (optional but handy for movement/impacts). */
        public double ux = 0.0, uy = 0.0;

        public Link(Entity fromPort, Entity toPort, double length) {
                this.fromPort = fromPort;
                this.toPort = toPort;
                this.length = Math.max(1e-6, length);
        }

        public Link(Entity fromPort, Entity toPort, double length, double ux, double uy) {
                this(fromPort, toPort, length);
                this.ux = ux;
                this.uy = uy;
        }
}
