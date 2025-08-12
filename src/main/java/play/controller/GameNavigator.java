package play.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import play.model.audio.AudioAssets;
import play.model.audio.GlobalAudio;
import play.model.settings.Settings;
import play.model.settings.SettingsStore;

public final class GameNavigator {
        private GameNavigator() {}

        public static void showMainMenu(Stage stage) {
                try {
                        Parent root = FXMLLoader.load(GameNavigator.class.getResource("/fxml/MainMenu.fxml"));
                        Scene scene = new Scene(root, 960, 600);
                        try {
                                scene.getStylesheets().addAll(
                                        GameNavigator.class.getResource("/css/theme.css").toExternalForm(),
                                        GameNavigator.class.getResource("/css/menu.css").toExternalForm()
                                );
                        } catch (Exception ignore) {}

                        stage.setScene(scene);
                        stage.setResizable(false);
                        stage.setTitle("Conduit Garden — Main Menu");

                        Settings s = SettingsStore.load();
                        stage.setFullScreen(s.fullScreen);

                        // BGM
                        if (s.musicEnabled) {
                                GlobalAudio.setCurrentBgmPath(AudioAssets.MENU);
                                GlobalAudio.get().playBackground(AudioAssets.MENU, true);
                        } else {
                                GlobalAudio.get().stopBackground();
                        }
                } catch (Exception ex) {
                        ex.printStackTrace();
                }
        }

        public static void startGame(Stage stage, String levelPath) {
                try {
                        FXMLLoader loader = new FXMLLoader(GameNavigator.class.getResource("/fxml/main.fxml"));
                        Parent root = loader.load();
                        MainController ctrl = loader.getController();
                        ctrl.initLevel(levelPath);

                        Scene scene = new Scene(root, 960, 600);
                        try {
                                scene.getStylesheets().addAll(
                                        GameNavigator.class.getResource("/css/theme.css").toExternalForm(),
                                        GameNavigator.class.getResource("/css/game.css").toExternalForm()
                                );
                        } catch (Exception ignore) {}

                        stage.setScene(scene);
                        stage.setResizable(false);
                        stage.setTitle("Conduit Garden — Gameplay");

                        Settings s = SettingsStore.load();
                        stage.setFullScreen(s.fullScreen);

                        // Gameplay BGM
                        if (s.musicEnabled) {
                                GlobalAudio.setCurrentBgmPath(AudioAssets.GAMEPLAY);
                                GlobalAudio.get().playBackground(AudioAssets.GAMEPLAY, true);
                        } else {
                                GlobalAudio.get().stopBackground();
                        }
                } catch (Exception ex) {
                        ex.printStackTrace();
                }
        }
}
