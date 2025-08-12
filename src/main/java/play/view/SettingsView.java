package play.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import play.model.audio.AudioService;
import play.model.settings.Settings;
import play.model.settings.SettingsStore;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Non-blocking Settings window.
 * - Reads/writes SettingsStore.
 * - Applies fullscreen on owner and volume/music on AudioService (if provided).
 * - Uses callbacks; does not block animation pulses (no showAndWait).
 */
public final class SettingsView {
        private SettingsView() {}

        /** Convenience: load, show, save+apply on OK. */
        public static void show(Stage owner) {
                show(owner, null, null);
        }

        /**
         * Professional API:
         * @param owner stage to own modality & to apply fullscreen on OK
         * @param audioService optional audio service to apply volume/music
         * @param afterClose optional callback after the window is hidden
         */
        public static void show(Stage owner, AudioService audioService, Runnable afterClose) {
                Objects.requireNonNull(owner, "owner stage required");

                // Load current settings
                Settings current = SettingsStore.load();

                // --- UI controls ---
                CheckBox fullScreenChk = new CheckBox("Full screen");
                fullScreenChk.setSelected(current.fullScreen);

                Slider volume = new Slider(0, 1, current.masterVolume);
                volume.setBlockIncrement(0.05);
                volume.setMajorTickUnit(0.25);
                volume.setShowTickMarks(true);
                volume.setShowTickLabels(true);
                Label volLabel = new Label(String.format("Volume: %d%%", (int)Math.round(current.masterVolume * 100)));
                volume.valueProperty().addListener((obs, o, v) ->
                        volLabel.setText("Volume: " + (int)Math.round(v.doubleValue()*100) + "%"));

                CheckBox musicChk = new CheckBox("Music enabled");
                musicChk.setSelected(current.musicEnabled);

                CheckBox sfxChk = new CheckBox("SFX enabled");
                sfxChk.setSelected(current.sfxEnabled);

                // Buttons
                Button btnApply   = new Button("Apply");
                Button btnCancel  = new Button("Cancel");
                Button btnDefault = new Button("Defaults");

                HBox btnRow = new HBox(10, btnDefault, new Region(), btnCancel, btnApply);
                HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
                btnRow.setAlignment(Pos.CENTER_RIGHT);

                // Layout
                GridPane form = new GridPane();
                form.setHgap(12);
                form.setVgap(10);
                int r = 0;
                form.add(fullScreenChk, 0, r++, 2, 1);
                form.add(volLabel,      0, r);
                form.add(volume,        1, r++);  // label + slider in same row
                form.add(musicChk,      0, r++, 2, 1);
                form.add(sfxChk,        0, r++, 2, 1);

                VBox root = new VBox(16, new Label("Settings"), form, btnRow);
                root.setPadding(new Insets(16));
                root.getStyleClass().add("settings-root");
                // Optional: style classes for theme.css if you have them
                volLabel.getStyleClass().add("muted");

                // Stage
                Stage stage = new Stage();
                Scene scene = new Scene(root, 420, 260);
                // Attach your CSS (safe if missing)
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

                // --- Behaviors (non-blocking) ---
                Runnable apply = () -> {
                        Settings updated = Settings
                                .defaults()
                                .withFullScreen(fullScreenChk.isSelected())
                                .withMasterVolume(volume.getValue())
                                .withMusicEnabled(musicChk.isSelected())
                                .withSfxEnabled(sfxChk.isSelected());

                        // Persist
                        SettingsStore.save(updated);

                        // Apply to owner (fullscreen)
                        owner.setFullScreen(updated.fullScreen);
                        // Hint off to avoid distracting overlay
                        owner.setFullScreenExitHint("");

                        // Apply to audio if available
                        if (audioService != null) {
                                audioService.setVolume(updated.masterVolume);
                                // Simple handling for enable/disable:
                                // If music disabled -> stop bgm; if enabled, caller decides what to play
                                if (!updated.musicEnabled) audioService.stopBackground();
                                // SFX enable/disable would be handled by checking before playSfx; persist here only
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

                stage.show(); // NON-BLOCKING
        }
}
