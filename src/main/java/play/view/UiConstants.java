package play.view;

/** Central place for UI size/color constants so visuals stay consistent. */
public final class UiConstants {
        private UiConstants() {}

        /** Visual size of a port (square edge / triangle bounding box) in pixels. */
        public static final double PORT_SIZE   = 12.0;

        /** Packets should look like ports but a bit more visible. */
        public static final double PACKET_SIZE = PORT_SIZE + 4.0; // e.g., 16px

        /** System rectangle size (independent from port/packet). */
        public static final double SYSTEM_SIZE = 64.0; // tweak as you like

        // Phase 2: bends
        public static final double BEND_DOT_RADIUS     = 4.5;    // px (visual)
        public static final double BEND_HIT_RADIUS     = 10.0;   // px (mouse hit-test)
        public static final double BEND_DRAG_MAX_RADIUS= 120.0;  // px from creation point
}
