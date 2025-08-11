package play.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Minimal level picker. Add more levels here if you add files. */
public final class StageSelectionView {
        private StageSelectionView() {}

        public static void show(Stage owner) {
                Dialog<String> dlg = new Dialog<>();
                dlg.initOwner(owner);
                dlg.initModality(Modality.APPLICATION_MODAL);
                dlg.setTitle("Select Stage");

                ToggleGroup group = new ToggleGroup();
                RadioButton l1 = new RadioButton("Level 1");
                l1.setUserData("/levels/level1.json");
                l1.setToggleGroup(group);
                l1.setSelected(true);

                RadioButton l2 = new RadioButton("Level 2");
                l2.setUserData("/levels/level2.json");
                l2.setToggleGroup(group);

                VBox box = new VBox(10, l1, l2);
                box.setStyle("-fx-padding:16;");
                dlg.getDialogPane().setContent(box);
                dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                dlg.setResultConverter(bt -> {
                        if (bt == ButtonType.OK && group.getSelectedToggle() != null) {
                                return (String) group.getSelectedToggle().getUserData();
                        }
                        return null;
                });

                dlg.showAndWait().ifPresent(path -> {
                        if (path != null) {
                                GameNavigator.startGame(owner, path);
                        }
                });
        }
}
