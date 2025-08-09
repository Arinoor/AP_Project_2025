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

        private AudioManager() { }

        public static AudioManager getInstance() {
                if (instance == null) {
                        instance = new AudioManager();
                }
                return instance;
        }

        /**
         * Plays background music from a resource path.
         * @param resourcePath Path to the audio file in resources (e.g. "/music/background.mp3")
         * @param loop Whether to loop the music indefinitely.
         */
        public void playBackground(String resourcePath, boolean loop) {
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
                        backgroundPlayer.setVolume(0.5);
                        backgroundPlayer.play();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        public void stopBackground() {
                if (backgroundPlayer != null) {
                        backgroundPlayer.stop();
                        backgroundPlayer.dispose();
                        backgroundPlayer = null;
                }
        }

        /**
         * Plays a short sound effect.
         * @param resourcePath Path to the sfx file in resources (e.g. "/sfx/deliver.wav")
         */
        public void playSfx(String resourcePath) {
                try {
                        AudioClip clip = sfxCache.get(resourcePath);
                        if (clip == null) {
                                URL res = getClass().getResource(resourcePath);
                                if (res == null) {
                                        System.err.println("SFX resource not found: " + resourcePath);
                                        return;
                                }
                                clip = new AudioClip(res.toExternalForm());
                                sfxCache.put(resourcePath, clip);
                        }
                        clip.play();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }
}
