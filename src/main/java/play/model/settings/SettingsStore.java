package play.model.settings;

import java.util.prefs.Preferences;

/** Persist settings with Java Preferences (user scope). */
public final class SettingsStore {
        private static final String NODE = "play.ap_project1.settings";

        private static final String K_FULL = "fullScreen";
        private static final String K_VOL  = "masterVolume";
        private static final String K_MUS  = "musicEnabled";
        private static final String K_SFX  = "sfxEnabled";

        private SettingsStore() {}

        public static Settings load() {
                Preferences p = Preferences.userRoot().node(NODE);
                boolean full = p.getBoolean(K_FULL, Settings.defaults().fullScreen);
                double vol   = p.getDouble(K_VOL,  Settings.defaults().masterVolume);
                boolean mus  = p.getBoolean(K_MUS, Settings.defaults().musicEnabled);
                boolean sfx  = p.getBoolean(K_SFX, Settings.defaults().sfxEnabled);
                return Settings.of(full, vol, mus, sfx);
        }

        public static void save(Settings s) {
                Preferences p = Preferences.userRoot().node(NODE);
                p.putBoolean(K_FULL, s.fullScreen);
                p.putDouble(K_VOL,   s.masterVolume);
                p.putBoolean(K_MUS,  s.musicEnabled);
                p.putBoolean(K_SFX,  s.sfxEnabled);
        }

        public static void reset() {
                save(Settings.defaults());
        }
}
