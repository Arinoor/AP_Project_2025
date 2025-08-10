package play.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AudioManager {

        private static AudioManager instance;
        private MediaPlayer backgroundPlayer;
        private final Map<String, AudioClip> sfxCache = new HashMap<>();
        private double volume = 0.5; // 0..1, remembered

        private AudioManager() { }

        public static AudioManager getInstance() {
                if (instance == null) {
                        instance = new AudioManager();
                }
                return instance;
        }

        /** Set global volume for bgm and sfx (0..1). */
        public synchronized void setVolume(double v) {
                volume = Math.max(0.0, Math.min(1.0, v));
                if (backgroundPlayer != null) {
                        backgroundPlayer.setVolume(volume);
                }
                // update all cached sfx volumes
                for (AudioClip clip : sfxCache.values()) {
                        clip.setVolume(volume);
                }
        }

        public synchronized double getVolume() { return volume; }

        /**
         * Plays background music from a resource path.
         * @param resourcePath Path to the audio file in resources (e.g. "/music/background.mp3")
         * @param loop Whether to loop the music indefinitely.
         */
        public synchronized void playBackground(String resourcePath, boolean loop) {
                try {
                        stopBackground();
                        URL res = getClass().getResource(resourcePath);
                        if (res == null) {
                                System.err.println("Background music resource not found: " + resourcePath);
                                return;
                        }
                        Media media = new Media(res.toExternalForm());
                        backgroundPlayer = new MediaPlayer(media);
                        backgroundPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                        backgroundPlayer.setVolume(volume); // use current volume
                        backgroundPlayer.play();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        public synchronized void stopBackground() {
                if (backgroundPlayer != null) {
                        backgroundPlayer.stop();
                        backgroundPlayer.dispose();
                        backgroundPlayer = null;
                }
        }

        /** Plays a short sound effect from resources. */
        public synchronized void playSfx(String resourcePath) {
                try {
                        AudioClip clip = sfxCache.get(resourcePath);
                        if (clip == null) {
                                URL res = getClass().getResource(resourcePath);
                                if (res == null) {
                                        System.err.println("SFX resource not found: " + resourcePath);
                                        return;
                                }
                                clip = new AudioClip(res.toExternalForm());
                                clip.setVolume(volume); // set initial volume
                                sfxCache.put(resourcePath, clip);
                        }
                        clip.play();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }
}
