package play.model.constants;

/** Central place for balance knobs and magic numbers. */
public final class GameBalance {
        private GameBalance() {}

        // Coin rewards
        public static final int COIN_REWARD_SQUARE   = 1;
        public static final int COIN_REWARD_TRIANGLE = 2;

        // Shop costs
        public static final int COST_ATAR     = 3; // disable impact waves 10s
        public static final int COST_AIRYAMAN = 4; // disable collisions 5s
        public static final int COST_ANAHITA  = 5; // reset packet noise now

        // Device buffer capacity (QueueSystem)
        public static final int DEVICE_CAPACITY = 5;
}
