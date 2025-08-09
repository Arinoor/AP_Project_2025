package play.components;

import play.core.Component;
import play.core.Entity;

public class Link implements Component {
        public final Entity fromPort;
        public final Entity toPort;
        public final double length;
        // logical parameter: how far along [0..1] a seed at position p is
        public Link(Entity fromPort, Entity toPort, double length){
                this.fromPort = fromPort; this.toPort = toPort; this.length = length;
        }
}
