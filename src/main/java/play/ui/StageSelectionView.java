package play.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class StageSelectionView {
        private StageSelectionView(){}

        public static void show(Stage owner) {
                Stage dlg = new Stage();
                dlg.initOwner(owner);
                dlg.initModality(Modality.APPLICATION_MODAL);
                dlg.setResizable(false);
                VBox root = new VBox(10);
                root.setPadding(new Insets(16));
                root.setStyle("-fx-background-color:#1b222b;");

                Label title = new Label("Select Stage");
                title.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:16; -fx-font-weight:bold;");

                Button level1 = new Button("Level 1");
                Button level2 = new Button("Level 2");
                Button cancel = new Button("Cancel");
                level1.setOnAction(e -> { dlg.close(); GameNavigator.startGame(owner, "/levels/level1.json"); });
                level2.setOnAction(e -> { dlg.close(); GameNavigator.startGame(owner, "/levels/level2.json"); });
                cancel.setOnAction(e -> dlg.close());

                root.getChildren().addAll(title, level1, level2, cancel);
                dlg.setScene(new Scene(root));
                dlg.setTitle("Stages");
                dlg.showAndWait();
        }
}
