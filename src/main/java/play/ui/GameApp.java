package play.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GameApp extends Application {
        @Override
        public void start(Stage stage) throws Exception {
                // Start at the Login / Main Menu
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.setTitle("Conduit Garden - Blueprint Hell");
                stage.setResizable(false); // menu is windowed; game scene will switch to locked full screen
                stage.show();
        }

        public static void main(String[] args) { launch(args); }
}
