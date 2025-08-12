package play.model.components;

import play.model.core.Entity;

public class Link {
        public final Entity fromPort;
        public final Entity toPort;
        public double length;

        public Link(Entity fromPort, Entity toPort) {
                this.fromPort = fromPort;
                this.toPort   = toPort;
                updateLength();
        }

        public void updateLength() {
                if (fromPort == null || toPort == null) {
                        length = 1.0;
                        return;
                }
                if (!fromPort.has(Transform.class) || !toPort.has(Transform.class)) {
                        length = 1.0;
                        return;
                }
                Transform a = fromPort.get(Transform.class);
                Transform b = toPort.get(Transform.class);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                length = Math.max(1.0, Math.hypot(dx, dy));
        }
}
