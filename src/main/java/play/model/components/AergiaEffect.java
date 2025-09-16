// New file: AergiaEffect.java
package play.model.components;

import play.model.core.Entity;

public class AergiaEffect {
        public final Link link;
        public final double position; // normalized position along the link [0-1]
        public double remainingTime;

        public AergiaEffect(Link link, double position, double duration) {
                this.link = link;
                this.position = position;
                this.remainingTime = duration;
        }
}