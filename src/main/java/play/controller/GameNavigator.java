package play.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

public final class GameNavigator {
        private GameNavigator() {}

        public static void showMainMenu(Stage stage) {
                try {
                        Parent root = FXMLLoader.load(GameNavigator.class.getResource("/fxml/MainMenu.fxml"));
                        Scene scene = new Scene(root);
                        scene.getStylesheets().addAll(
                                GameNavigator.class.getResource("/css/theme.css").toExternalForm(),
                                GameNavigator.class.getResource("/css/menu.css").toExternalForm()
                        );

                        stage.setScene(scene);
                        stage.setFullScreenExitHint("");
                        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
                        stage.setFullScreen(true);
                        stage.show();
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

                        Scene scene = new Scene(root);
                        scene.getStylesheets().addAll(
                                GameNavigator.class.getResource("/css/theme.css").toExternalForm(),
                                GameNavigator.class.getResource("/css/game.css").toExternalForm()
                        );

                        stage.setScene(scene);
                        stage.setFullScreenExitHint("");
                        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
                        stage.setFullScreen(true);
                        stage.show();
                } catch (Exception ex) {
                        ex.printStackTrace();
                }
        }
}
