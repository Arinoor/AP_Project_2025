package play.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class MainMenuController {
        @FXML private Button startBtn;
        @FXML private Button stagesBtn;
        @FXML private Button settingsBtn;
        @FXML private Button exitBtn;

        @FXML
        private void initialize() {
                startBtn.setOnAction(e -> GameNavigator.startGame(stage(), "/levels/level1.json"));
                stagesBtn.setOnAction(e -> StageSelectionView.show(stage()));
                settingsBtn.setOnAction(e -> SettingsView.show(stage()));
                exitBtn.setOnAction(e -> Platform.exit());
        }

        private Stage stage() {
                return (Stage) startBtn.getScene().getWindow();
        }
}
