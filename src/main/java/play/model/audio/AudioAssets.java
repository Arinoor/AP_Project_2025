package play.model.audio;

import java.util.HashMap;
import java.util.Map;

/** Centralized audio asset paths + optional per-track gain. */
public final class AudioAssets {
        private AudioAssets() {}

        // --- Background music (BGM)
        public static final String MENU      = "/audio/bgm/menu_theme.mp3";
        public static final String GAMEPLAY  = "/audio/bgm/gameplay_theme.mp3";
        public static final String VICTORY   = "/audio/bgm/victory_theme.wav";
        public static final String GAMEOVER  = "/audio/bgm/gameover_theme.wav";

        // --- Sound effects (SFX)
        public static final String COLLISION     = "/audio/sfx/collision.wav";
        public static final String CLICK         = "/audio/sfx/click.wav";
        public static final String ERROR         = "/audio/sfx/error.wav";
        public static final String PURCHASE      = "/audio/sfx/purchase.wav";
        public static final String WIRE_CONNECT  = "/audio/sfx/wire_connect.wav";

        /** Optional per-BGM pre-gain in dB (applied before master volume). */
        private static final Map<String, Double> BGM_GAIN_DB = new HashMap<>();
        static {
                // Tune these if any track feels too quiet/loud compared to SFX.
                // +3 dB ≈ 1.41×, +4 dB ≈ 1.58×, +5 dB ≈ 1.78×
                BGM_GAIN_DB.put(MENU,     4.0);  // menu is often quieter
                BGM_GAIN_DB.put(GAMEPLAY, 5.0);  // action track tends to be mastered softer
                BGM_GAIN_DB.put(VICTORY,  3.0);
                BGM_GAIN_DB.put(GAMEOVER, 3.0);
        }

        public static double bgmGainLinear(String bgmPath) {
                Double db = BGM_GAIN_DB.get(bgmPath);
                if (db == null) return 1.0;
                return Math.pow(10.0, db / 20.0);
        }
}
