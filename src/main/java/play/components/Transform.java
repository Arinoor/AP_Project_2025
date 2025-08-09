package play.components;

import play.core.Component;

/** Position data for rendering/movement */
public class Transform implements Component {
        public double x, y;
        public Transform(double x, double y){ this.x = x; this.y = y; }
}
