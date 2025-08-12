package play.infrastructure.audio;

import play.model.audio.AudioService;
import play.model.audio.GlobalAudio;

/**
 * Back-compat shim. Old code calls Audio.get(); we now route it to GlobalAudio.
 * No manual initialization needed anymore.
 */
public final class Audio {
        private Audio() {}

        /** Always returns the single global AudioService instance. */
        public static AudioService get() {
                return GlobalAudio.get();
        }

        /** Kept for compatibility; no-op now. */
        public static void init(AudioService ignored) {
                // no-op: GlobalAudio is eagerly available
        }
}
