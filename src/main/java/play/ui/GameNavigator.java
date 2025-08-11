package play.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Central place to swap between Main Menu and Game scenes. */
public final class GameNavigator {
        private GameNavigator() {}

        /** Show the Main Menu scene on the given Stage. */
        public static void showMainMenu(Stage stage) {
                try {
                        Parent root = FXMLLoader.load(GameNavigator.class.getResource("/fxml/MainMenu.fxml"));
                        Scene scene = new Scene(root, 960, 600);
                        stage.setTitle("Conduit Garden — Main Menu");
                        stage.setResizable(false);
                        stage.setScene(scene);
                        stage.show();
                } catch (Exception ex) {
                        ex.printStackTrace();
                }
        }

        /** Start the game by loading main.fxml and initializing the chosen level. */
        public static void startGame(Stage stage, String levelPath) {
                try {
                        FXMLLoader loader = new FXMLLoader(GameNavigator.class.getResource("/fxml/main.fxml"));
                        Parent root = loader.load();
                        MainController ctrl = loader.getController();
                        ctrl.initLevel(levelPath);

                        Scene scene = new Scene(root, 960, 600);
                        stage.setTitle("Conduit Garden — Gameplay");
                        stage.setResizable(false);
                        stage.setScene(scene);
                        stage.show();
                } catch (Exception ex) {
                        ex.printStackTrace();
                }
        }
}
