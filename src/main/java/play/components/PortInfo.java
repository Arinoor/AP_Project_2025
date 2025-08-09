package play.components;

import play.core.Component;

/** Port meta */
public class PortInfo implements Component {
        public enum IO { IN, OUT }
        public enum Shape { SQUARE, TRIANGLE }
        public final IO io;
        public final Shape shape;
        public PortInfo(IO io, Shape shape) { this.io = io; this.shape = shape; }
}
