package play.components;

import play.core.Component;
import play.core.Entity;

/** Logical link entity between two port-entities */
public class Link implements Component {
        public final Entity fromPort;
        public final Entity toPort;
        public final double length;
        public Link(Entity fromPort, Entity toPort, double length){
                this.fromPort = fromPort; this.toPort = toPort; this.length = length;
        }
}
