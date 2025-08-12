package play.model.audio;

import play.infrastructure.audio.JavaFxAudioService;

/** Singleton bridge so views/controllers/systems share one audio instance. */
public final class GlobalAudio {
        private static final AudioService INSTANCE = new JavaFxAudioService();
        private static String currentBgm = null;

        private GlobalAudio() {}

        public static AudioService get() {
                return INSTANCE;
        }

        /** Track currently requested BGM so settings can restart it after changes. */
        public static void setCurrentBgmPath(String path) {
                currentBgm = path;
        }

        public static String currentBgmPath() {
                return currentBgm;
        }
}
