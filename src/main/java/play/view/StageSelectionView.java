package play.view;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;

/** Minimal level picker dialog. Calls back with the chosen level path. */
public final class StageSelectionView {
        private StageSelectionView() {}

        /** Show a modal dialog to pick a level; onSelected receives the chosen classpath (e.g., "/levels/level1.json"). */
        public static void show(Stage owner, Consumer<String> onSelected) {
                Dialog<String> dlg = new Dialog<>();
                if (owner != null) {
                        dlg.initOwner(owner);
                        dlg.initModality(Modality.WINDOW_MODAL);
                } else {
                        dlg.initModality(Modality.APPLICATION_MODAL);
                }
                dlg.setTitle("Select Stage");

                ToggleGroup group = new ToggleGroup();
                RadioButton l1 = new RadioButton("Level 1");
                l1.setUserData("/levels/level1.json");
                l1.setToggleGroup(group);
                l1.setSelected(true);

                RadioButton l2 = new RadioButton("Level 2");
                l2.setUserData("/levels/level2.json");
                l2.setToggleGroup(group);

                RadioButton l3 = new RadioButton("Level 3");
                l3.setUserData("/levels/level3.json");
                l3.setToggleGroup(group);

                RadioButton l4 = new RadioButton("Level 4");
                l4.setUserData("/levels/level4.json");
                l4.setToggleGroup(group);

                VBox box = new VBox(10, l1, l2, l3, l4);
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
                        if (onSelected != null) onSelected.accept(path);
                });
        }
}
