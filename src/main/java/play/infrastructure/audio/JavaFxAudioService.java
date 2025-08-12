package play.infrastructure.audio;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import play.model.audio.AudioService;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class JavaFxAudioService implements AudioService {
        private MediaPlayer backgroundPlayer;
        private final Map<String, AudioClip> sfxCache = new HashMap<>();
        private double volume = 0.5;

        @Override public void setVolume(double v) {
                volume = Math.max(0.0, Math.min(1.0, v));
                if (backgroundPlayer != null) backgroundPlayer.setVolume(volume);
                sfxCache.values().forEach(c -> c.setVolume(volume));
        }

        @Override public double getVolume() { return volume; }

        @Override public void playBackground(String resourcePath, boolean loop) {
                stopBackground();
                URL res = getClass().getResource(resourcePath);
                if (res == null) { System.err.println("Background music resource not found: " + resourcePath); return; }
                Media media = new Media(res.toExternalForm());
                backgroundPlayer = new MediaPlayer(media);
                backgroundPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                backgroundPlayer.setVolume(volume);
                backgroundPlayer.play();
        }

        @Override public void stopBackground() {
                if (backgroundPlayer != null) {
                        backgroundPlayer.stop();
                        backgroundPlayer.dispose();
                        backgroundPlayer = null;
                }
        }

        @Override public void playSfx(String resourcePath) {
                AudioClip clip = sfxCache.get(resourcePath);
                if (clip == null) {
                        URL res = getClass().getResource(resourcePath);
                        if (res == null) { System.err.println("SFX resource not found: " + resourcePath); return; }
                        clip = new AudioClip(res.toExternalForm());
                        clip.setVolume(volume);
                        sfxCache.put(resourcePath, clip);
                }
                clip.play();
        }
}
