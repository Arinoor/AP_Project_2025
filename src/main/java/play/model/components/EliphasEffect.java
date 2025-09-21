package play.model.components;

public class EliphasEffect {
        public final Link link;
        public final double position; // normalized position along the link [0-1]
        public double remainingTime;

        public EliphasEffect(Link link, double position, double duration) {
                this.link = link;
                this.position = position;
                this.remainingTime = duration;
        }
}