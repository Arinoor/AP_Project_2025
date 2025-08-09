package play.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioManager {
        private static final AudioManager INSTANCE = new AudioManager();
        private MediaPlayer background;
        private double bgVolume = 0.25;
        private final ExecutorService pool = Executors.newCachedThreadPool();

        private AudioManager() {}

        public static AudioManager getInstance() { return INSTANCE; }

        /**
         * resourcePath should start with a '/', e.g. "/music/background.mp3"
         */
        public void playBackground(String resourcePath, boolean loop) {
                try {
                        URL url = getResourceUrl(resourcePath);
                        if (url == null) return;
                        if (background != null) background.stop();
                        Media m = new Media(url.toExternalForm());
                        background = new MediaPlayer(m);
                        background.setVolume(bgVolume);
                        background.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                        background.play();
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        public void stopBackground() {
                if (background != null) background.stop();
        }

        public void setBackgroundVolume(double v) {
                bgVolume = Math.max(0.0, Math.min(1.0, v));
                if (background != null) background.setVolume(bgVolume);
        }

        /**
         * resourcePath should start with '/', for example "/sfx/deliver.wav"
         */
        public void playSfx(String resourcePath) {
                pool.submit(() -> {
                        try {
                                URL url = getResourceUrl(resourcePath);
                                if (url == null) return;
                                AudioClip clip = new AudioClip(url.toExternalForm());
                                clip.play();
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                });
        }

        private URL getResourceUrl(String resourcePath) {
                // try given path then try with leading slash
                URL url = getClass().getResource(resourcePath);
                if (url == null) url = getClass().getResource("/" + resourcePath.replaceFirst("^/", ""));
                return url;
        }

        public void shutdown() {
                pool.shutdown();
                if (background != null) background.dispose();
        }
}
