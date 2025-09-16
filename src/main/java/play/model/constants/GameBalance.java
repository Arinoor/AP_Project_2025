package play.model.constants;

/** Central place for balance knobs and magic numbers. */
public final class GameBalance {
        private GameBalance() {}

        // Coin rewards
        public static final int COIN_REWARD_SQUARE   = 2;
        public static final int COIN_REWARD_TRIANGLE = 3;
        public static final int COIN_REWARD_INFINITE = 1;
        public static final int COIN_REWARD_SECURE_PROTECTED = 4;

        // Shop costs
        public static final int COST_ATAR     = 3; // disable impact waves 10s
        public static final int COST_AIRYAMAN = 4; // disable collisions 5s
        public static final int COST_ANAHITA  = 5; // reset packet noise now

        // Phase 2: Bend cost & caps
        public static final int  COST_BEND_PER_LINK_CREATE = 1; // pay once per new bend
        public static final int  MAX_BENDS_PER_LINK        = 3;

        // Device buffer capacity (QueueSystem)
        public static final int DEVICE_CAPACITY = 5;

        // Movement/physics
        public static final double SQUARE_BASE_SPEED         = 120.0; // px/s
        public static final double TRIANGLE_BASE_SPEED       = 140.0; // px/s
        public static final double TRIANGLE_INCOMPAT_ACCEL   = 220.0; // px/s^2

        public static final double LATERAL_DAMP              = 0.98;  // per tick factor
        public static final double IMPACT_COOLDOWN_OFF       = 0.15;  // energy threshold

        // ---- Packet-entry penalties ----
        public static final double ENTRY_SPEED_LIMIT = 300.0;     // px/s (tune as needed)
        public static final double SYSTEM_DISABLE_SECONDS = 5.0;   // seconds a system stays inactive

        // --- Trojan / Antitrojan knobs
        /** Probability that a saboteur will tag an (unprotected) arriving packet as trojan. */
        public static final double SABOTEUR_TROJAN_PROB = 0.30; // tune: 0.0..1.0

        /** Antitrojan AoE radius (pixels) to inspect packets. */
        public static final double ANTITROJAN_RADIUS = 120.0;

        /** Seconds the Antitrojan system stays disabled after cleaning one trojan. */
        public static final double ANTITROJAN_DISABLE_SECONDS = 6.0;

        /** How many heavy passes a wire tolerates before being destroyed. */
        public static final int HEAVY_MAX_PASSES = 3;

        /** Probability a HEAVY entering a system will flip the input port's shape (0..1). */
        public static final double HEAVY_PORT_TOGGLE_PROB = 0.30;

        /** Seconds Merge system waits before combining collected bitpackets. */
        public static final double MERGE_WAIT_SECONDS = 10;

        /** HEAVY default straight speed (px/s). Tune if needed. */
        public static final double HEAVY_STRAIGHT_SPEED = 80.0;

        /** HEAVY acceleration on curved links (px/s^2). */
        public static final double HEAVY_CURVE_ACCEL = 40.0;


}

