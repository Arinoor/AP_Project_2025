package play.controller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import play.model.audio.GlobalAudio;
import play.model.audio.AudioAssets;
import play.model.settings.Settings;
import play.model.settings.SettingsStore;

public class GameApp extends Application {
        @Override
        public void start(Stage stage) throws Exception {
                // Load settings
                Settings s = SettingsStore.load();

                // Stage chrome + fullscreen
                stage.initStyle(StageStyle.UNDECORATED);
                stage.setFullScreenExitHint("");
                stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
                stage.setFullScreen(s.fullScreen);

                // Initial volume -> global audio
                GlobalAudio.get().setVolume(s.masterVolume);

                // Launch Main Menu
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 960, 600);
                try {
                        scene.getStylesheets().addAll(
                                getClass().getResource("/css/theme.css").toExternalForm(),
                                getClass().getResource("/css/menu.css").toExternalForm()
                        );
                } catch (Exception ignore) {}

                stage.setScene(scene);
                stage.setTitle("Conduit Garden");
                stage.setResizable(false);
                stage.show();

                // Start menu BGM (respect toggle)
                if (s.musicEnabled) {
                        GlobalAudio.setCurrentBgmPath(AudioAssets.MENU);
                        GlobalAudio.get().playBackground(AudioAssets.MENU, true);
                } else {
                        GlobalAudio.get().stopBackground();
                }
        }

        public static void main(String[] args) { launch(args); }
}
