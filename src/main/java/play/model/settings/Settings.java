package play.model.settings;

/** Immutable app settings (extend safely later). */
public final class Settings {
        public final boolean fullScreen;
        public final double masterVolume;  // 0..1
        public final boolean musicEnabled;
        public final boolean sfxEnabled;

        private Settings(boolean fullScreen, double masterVolume, boolean musicEnabled, boolean sfxEnabled) {
                this.fullScreen = fullScreen;
                this.masterVolume = clamp01(masterVolume);
                this.musicEnabled = musicEnabled;
                this.sfxEnabled = sfxEnabled;
        }

        public static Settings of(boolean fullScreen, double masterVolume, boolean musicEnabled, boolean sfxEnabled) {
                return new Settings(fullScreen, masterVolume, musicEnabled, sfxEnabled);
        }

        public static Settings defaults() {
                return new Settings(true, 0.6, true, true);
        }

        public Settings withFullScreen(boolean v) { return new Settings(v, masterVolume, musicEnabled, sfxEnabled); }
        public Settings withMasterVolume(double v) { return new Settings(fullScreen, v, musicEnabled, sfxEnabled); }
        public Settings withMusicEnabled(boolean v) { return new Settings(fullScreen, masterVolume, v, sfxEnabled); }
        public Settings withSfxEnabled(boolean v) { return new Settings(fullScreen, masterVolume, musicEnabled, v); }

        private static double clamp01(double v) { return (v < 0) ? 0 : (v > 1) ? 1 : v; }
}
