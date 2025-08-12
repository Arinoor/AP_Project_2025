package play.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class GameApp extends javafx.application.Application {
        @Override
        public void start(Stage stage) throws Exception {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root);

                // styles
                scene.getStylesheets().addAll(
                        getClass().getResource("/css/theme.css").toExternalForm(),
                        getClass().getResource("/css/menu.css").toExternalForm()
                );

                // fullscreen, no OS chrome
                stage.initStyle(StageStyle.UNDECORATED);
                stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
                stage.setFullScreenExitHint("");
                stage.setFullScreen(true);

                stage.setScene(scene);
                stage.setTitle("Conduit Garden");
                stage.setResizable(false);
                stage.show();
        }

        public static void main(String[] args) { launch(args); }
}
