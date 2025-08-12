package play.infrastructure.audio;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import play.model.audio.AudioAssets;
import play.model.audio.AudioService;
import play.model.settings.Settings;
import play.model.settings.SettingsStore;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class JavaFxAudioService implements AudioService {
        private MediaPlayer backgroundPlayer;
        private String currentBgmPath = null;

        private final Map<String, AudioClip> sfxCache = new HashMap<>();

        /** Master volume [0..1], controlled by SettingsView. */
        private double masterVolume = 0.7;

        /** Mix: keep SFX a bit quieter than music by default. */
        private static final double MUSIC_REL = 1.0;
        private static final double SFX_REL   = 0.65;

        @Override
        public synchronized void setVolume(double v) {
                masterVolume = Math.max(0.0, Math.min(1.0, v));

                // Apply to BGM with track-specific pre-gain
                if (backgroundPlayer != null) {
                        double pre = (currentBgmPath != null) ? AudioAssets.bgmGainLinear(currentBgmPath) : 1.0;
                        backgroundPlayer.setVolume(clamp01(masterVolume * MUSIC_REL * pre));
                }

                // Apply to cached SFX
                for (AudioClip c : sfxCache.values()) {
                        c.setVolume(clamp01(masterVolume * SFX_REL));
                }
        }

        @Override
        public synchronized double getVolume() {
                return masterVolume;
        }

        @Override
        public synchronized void playBackground(String resourcePath, boolean loop) {
                Settings s = SettingsStore.load();
                if (!s.musicEnabled) return;

                stopBackground();
                currentBgmPath = resourcePath;

                try {
                        URL res = getClass().getResource(resourcePath);
                        if (res == null) {
                                System.err.println("[AUDIO] BGM missing: " + resourcePath);
                                return;
                        }
                        Media media = new Media(res.toExternalForm());
                        media.setOnError(() ->
                                System.err.println("[AUDIO] Media error (bgm): " + media.getError())
                        );
                        backgroundPlayer = new MediaPlayer(media);
                        backgroundPlayer.setOnError(() ->
                                System.err.println("[AUDIO] Player error (bgm): " + backgroundPlayer.getError())
                        );
                        backgroundPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);

                        double pre = AudioAssets.bgmGainLinear(resourcePath);
                        backgroundPlayer.setVolume(clamp01(masterVolume * MUSIC_REL * pre));
                        backgroundPlayer.play();
                } catch (MediaException me) {
                        System.err.println("[AUDIO] Failed to play bgm: " + me);
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        @Override
        public synchronized void stopBackground() {
                if (backgroundPlayer != null) {
                        try { backgroundPlayer.stop(); } catch (Exception ignore) {}
                        try { backgroundPlayer.dispose(); } catch (Exception ignore) {}
                        backgroundPlayer = null;
                }
                currentBgmPath = null;
        }

        @Override
        public synchronized void playSfx(String resourcePath) {
                Settings s = SettingsStore.load();
                if (!s.sfxEnabled) return;

                AudioClip clip = sfxCache.get(resourcePath);
                if (clip == null) {
                        URL res = getClass().getResource(resourcePath);
                        if (res == null) {
                                System.err.println("[AUDIO] SFX missing: " + resourcePath);
                                return;
                        }
                        try {
                                // Preferred low-latency path (PCM WAV/AIFF/MP3)
                                clip = new AudioClip(res.toExternalForm());
                                clip.setVolume(clamp01(masterVolume * SFX_REL));
                                sfxCache.put(resourcePath, clip);
                        } catch (MediaException me) {
                                // Fallback for compressed WAVs
                                System.err.println("[AUDIO] AudioClip unsupported for " + resourcePath + " -> fallback to MediaPlayer: " + me);
                                try {
                                        Media media = new Media(res.toExternalForm());
                                        media.setOnError(() -> System.err.println("[AUDIO] Media error (sfx): " + media.getError()));
                                        MediaPlayer p = new MediaPlayer(media);
                                        p.setOnError(() -> System.err.println("[AUDIO] Player error (sfx): " + p.getError()));
                                        p.setVolume(clamp01(masterVolume * SFX_REL));
                                        p.setCycleCount(1);
                                        p.setOnEndOfMedia(() -> {
                                                try { p.stop(); } catch (Exception ignore) {}
                                                try { p.dispose(); } catch (Exception ignore) {}
                                        });
                                        p.play();
                                        return;
                                } catch (Exception ex) {
                                        System.err.println("[AUDIO] Fallback failed for " + resourcePath);
                                        ex.printStackTrace();
                                        return;
                                }
                        } catch (Exception e) {
                                e.printStackTrace();
                                return;
                        }
                }
                try {
                        clip.setVolume(clamp01(masterVolume * SFX_REL));
                        clip.play();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        private static double clamp01(double v) {
                return (v < 0) ? 0 : (v > 1) ? 1 : v;
        }
}
