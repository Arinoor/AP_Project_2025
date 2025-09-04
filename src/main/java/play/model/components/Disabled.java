package play.model.components;

/** Marks a system as disabled (inactive) for `remaining` seconds. */
public final class Disabled {
        public double remaining;

        public Disabled(double seconds) {
                this.remaining = seconds;
        }
}
