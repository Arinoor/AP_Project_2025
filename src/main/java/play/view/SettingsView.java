package play.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import play.model.audio.GlobalAudio;
import play.model.settings.Settings;
import play.model.settings.SettingsStore;

import java.util.Objects;

public final class SettingsView {
        private SettingsView() {}

        public static void show(Stage owner) {
                show(owner, null, null); // keep API backward-compatible
        }

        public static void show(Stage owner, Object ignored, Runnable afterClose) {
                Objects.requireNonNull(owner, "owner stage required");

                Settings current = SettingsStore.load();

                CheckBox fullScreenChk = new CheckBox("Full screen");
                fullScreenChk.setSelected(current.fullScreen);

                Slider volume = new Slider(0, 1, current.masterVolume);
                volume.setBlockIncrement(0.05);
                volume.setMajorTickUnit(0.25);
                volume.setShowTickMarks(true);
                volume.setShowTickLabels(true);

                Label volLabel = new Label(String.format("Volume: %d%%", (int)Math.round(current.masterVolume * 100)));

                // LIVE volume update via GlobalAudio
                volume.valueProperty().addListener((obs, o, v) -> {
                        double vol = v.doubleValue();
                        volLabel.setText("Volume: " + (int) Math.round(vol * 100) + "%");
                        GlobalAudio.get().setVolume(vol);
                });

                CheckBox musicChk = new CheckBox("Music enabled");
                musicChk.setSelected(current.musicEnabled);

                CheckBox sfxChk = new CheckBox("SFX enabled");
                sfxChk.setSelected(current.sfxEnabled);

                Button btnApply   = new Button("Apply");
                Button btnCancel  = new Button("Cancel");
                Button btnDefault = new Button("Defaults");

                HBox btnRow = new HBox(10, btnDefault, new Region(), btnCancel, btnApply);
                HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
                btnRow.setAlignment(Pos.CENTER_RIGHT);

                GridPane form = new GridPane();
                form.setHgap(12);
                form.setVgap(10);
                int r = 0;
                form.add(fullScreenChk, 0, r++, 2, 1);
                form.add(volLabel, 0, r);
                form.add(volume, 1, r++);
                form.add(musicChk, 0, r++, 2, 1);
                form.add(sfxChk, 0, r++, 2, 1);

                VBox root = new VBox(16, new Label("Settings"), form, btnRow);
                root.setPadding(new Insets(16));
                root.getStyleClass().add("settings-root");
                volLabel.getStyleClass().add("muted");

                Stage stage = new Stage();
                Scene scene = new Scene(root, 420, 260);
                try {
                        scene.getStylesheets().addAll(
                                SettingsView.class.getResource("/css/theme.css").toExternalForm(),
                                SettingsView.class.getResource("/css/menu.css").toExternalForm()
                        );
                } catch (Exception ignore) {}

                stage.setScene(scene);
                stage.setTitle("Game Settings");
                stage.initOwner(owner);
                stage.initModality(Modality.WINDOW_MODAL);
                stage.setResizable(false);

                Runnable apply = () -> {
                        Settings updated = Settings
                                .defaults()
                                .withFullScreen(fullScreenChk.isSelected())
                                .withMasterVolume(volume.getValue())
                                .withMusicEnabled(musicChk.isSelected())
                                .withSfxEnabled(sfxChk.isSelected());

                        // Persist
                        SettingsStore.save(updated);

                        // Apply fullscreen
                        owner.setFullScreen(updated.fullScreen);
                        owner.setFullScreenExitHint("");

                        // Apply audio: volume already live; now toggle music on/off
                        if (!updated.musicEnabled) {
                                GlobalAudio.get().stopBackground();
                        } else {
                                String bgm = GlobalAudio.currentBgmPath();
                                if (bgm != null) {
                                        GlobalAudio.get().playBackground(bgm, true);
                                }
                        }
                };

                btnApply.setDefaultButton(true);
                btnApply.setOnAction(e -> {
                        apply.run();
                        stage.hide();
                });

                btnCancel.setCancelButton(true);
                btnCancel.setOnAction(e -> stage.hide());

                btnDefault.setOnAction(e -> {
                        Settings d = Settings.defaults();
                        fullScreenChk.setSelected(d.fullScreen);
                        volume.setValue(d.masterVolume);
                        musicChk.setSelected(d.musicEnabled);
                        sfxChk.setSelected(d.sfxEnabled);
                });

                stage.setOnHidden(ev -> {
                        if (afterClose != null) afterClose.run();
                });

                stage.show();
        }
}
