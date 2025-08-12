package play.view;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Placeholder settings dialog (safe: won’t touch audio, etc.). */
public final class SettingsView {
        private SettingsView() {}

        public static void show(Stage owner) {
                Alert a = new Alert(Alert.AlertType.INFORMATION,
                        "Settings will be added soon.\n\n(Current build focuses on gameplay.)",
                        ButtonType.OK);
                a.initOwner(owner);
                a.initModality(Modality.APPLICATION_MODAL);
                a.setTitle("Game Settings");
                a.setHeaderText("Settings");
                a.showAndWait();
        }
}
