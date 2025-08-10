package play.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class GameNavigator {
        private GameNavigator(){}

        /** Show the main menu on the current stage. */
        public static void showMenu(Stage stage) {
                try {
                        Parent root = FXMLLoader.load(GameNavigator.class.getResource("/fxml/MainMenu.fxml"));
                        stage.setScene(new Scene(root));
                        stage.setResizable(false);
                        stage.setFullScreen(false);
                        stage.setMaximized(false);
                        stage.setTitle("Conduit Garden - Menu");
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }

        /** Load the main game scene and lock the window (non-movable, non-resizable, no close). */
        public static void startGame(Stage stage, String levelPath) {
                try {
                        FXMLLoader loader = new FXMLLoader(GameNavigator.class.getResource("/fxml/main.fxml"));
                        Parent root = loader.load();
                        MainController ctrl = loader.getController();
                        ctrl.initLevel(levelPath); // new hook—safe if levelPath==null

                        Scene scene = new Scene(root);
                        stage.setScene(scene);
                        // Lock the frame as requested
                        stage.setResizable(false);
                        stage.setMaximized(false);
                        stage.setFullScreen(true);         // hides system chrome, prevents moving/resizing
                        stage.setFullScreenExitHint("");
                        stage.setOnCloseRequest(evt -> evt.consume()); // prevent closing
                        stage.setTitle("Conduit Garden");
                        stage.show();
                        root.requestFocus(); // so key events work immediately
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }
}
