package play.model.audio;

public interface AudioService {
        void setVolume(double v);
        double getVolume();
        void playBackground(String resourcePath, boolean loop);
        void stopBackground();
        void playSfx(String resourcePath);
}
