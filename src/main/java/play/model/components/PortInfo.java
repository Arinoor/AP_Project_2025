package play.model.components;

import play.model.core.Entity;

public class PortInfo {
        public enum IO { IN, OUT }
        public enum Shape { SQUARE, TRIANGLE }

        public final IO io;
        public final Shape shape;
        public final Entity parentSystem;

        public PortInfo(IO io, Shape shape, Entity parentSystem) {
                this.io = io;
                this.shape = shape;
                this.parentSystem = parentSystem;
        }
}
