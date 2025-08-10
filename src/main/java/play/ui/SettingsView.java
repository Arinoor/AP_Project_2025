package play.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import play.audio.AudioManager;

public final class SettingsView {
        private SettingsView(){}

        public static void show(Stage owner) {
                Stage dlg = new Stage();
                dlg.initOwner(owner);
                dlg.initModality(Modality.APPLICATION_MODAL);
                dlg.setResizable(false);

                VBox root = new VBox(12);
                root.setPadding(new Insets(16));
                root.setStyle("-fx-background-color:#1b222b;");

                Label title = new Label("Settings");
                title.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:16; -fx-font-weight:bold;");

                Label volLabel = new Label("Volume");
                volLabel.setStyle("-fx-text-fill:#9da8b3;");
                Slider volume = new Slider(0, 100, 50);
                volume.valueProperty().addListener((o,ov,nv) -> AudioManager.getInstance().playBackground("/music/background.mp3", true));
                volume.valueProperty().addListener((o,ov,nv) -> AudioManager.getInstance().setVolume(nv.doubleValue()/100.0));

                Button close = new Button("Close");
                close.setOnAction(e -> dlg.close());

                HBox h = new HBox(10, volLabel, volume);
                root.getChildren().addAll(title, h, close);
                dlg.setScene(new Scene(root));
                dlg.setTitle("Settings");
                dlg.showAndWait();
        }
}
