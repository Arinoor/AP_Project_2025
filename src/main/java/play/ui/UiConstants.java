package play.ui;

/** Central place for UI size/color constants so visuals stay consistent. */
public final class UiConstants {
        private UiConstants() {}

        /** Visual size of a port (square edge / triangle bounding box) in pixels. */
        public static final double PORT_SIZE   = 12.0;

        /** Packets should look like ports but a bit more visible. */
        public static final double PACKET_SIZE = PORT_SIZE + 4.0; // e.g., 16px

        /** System rectangle size (independent from port/packet). */
        public static final double SYSTEM_SIZE = 64.0; // tweak as you like
}
