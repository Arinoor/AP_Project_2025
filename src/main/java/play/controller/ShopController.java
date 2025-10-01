package play.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import play.infrastructure.audio.Audio;
import play.model.audio.AudioAssets;
import play.model.constants.GameBalance;
import play.model.core.Entity;
import play.model.components.Seed;
import play.model.engine.GameEngine;
import play.model.systems.ShopSystem;

public class ShopController {
        @FXML private Button btnAtar;
        @FXML private Button btnAiryaman;
        @FXML private Button btnAnahita;
        @FXML private Button btnAergia;
        @FXML private Button closeBtn;
        @FXML private Button btnSisyphus;
        @FXML private Label  lblAtarMsg;
        @FXML private Label  lblAiryMsg;
        @FXML private Label  lblAnaMsg;
        @FXML private Label lblAergiaMsg;
        @FXML private Label  coinsInfo;
        @FXML private Label lblSisyphusMsg;

        private MainController mainController;


        private GameEngine engine;
        private ShopSystem shop;

        public void init(MainController mainController, GameEngine engine, ShopSystem shop) {
                this.mainController = mainController;
                this.engine = engine;
                this.shop = shop;
                refreshCoins();
                clearStatus();
        }


        @FXML private void onAtar() {
                if (shop.buyAtar()) {
                        lblAtarMsg.setText("Impact waves disabled for 10s.");
                        Audio.get().playSfx(AudioAssets.PURCHASE);
                } else {
                        lblAtarMsg.setText("Need 3 coins");
                        Audio.get().playSfx(AudioAssets.ERROR);
                }
                refreshCoins();
        }

        @FXML private void onAiryaman() {
                if (shop.buyAiryaman()) {
                        lblAiryMsg.setText("Collisions disabled for 5s.");
                        Audio.get().playSfx(AudioAssets.PURCHASE);
                } else {
                        lblAiryMsg.setText("Need 4 coins");
                        Audio.get().playSfx(AudioAssets.ERROR);
                }
                refreshCoins();
        }

        @FXML private void onAnahita() {
                if (shop.buyAnahita()) {
                        for (Entity e : engine.entities()) if (e.has(Seed.class)) {
                                e.get(Seed.class).collisions = 0;
                                e.get(Seed.class).lateral = 0.0;
                        }
                        lblAnaMsg.setText("All packet noise reset.");
                        Audio.get().playSfx(AudioAssets.PURCHASE);
                } else {
                        lblAnaMsg.setText("Not enough coins (need 5).");
                        Audio.get().playSfx(AudioAssets.ERROR);
                }
                refreshCoins();
        }

        @FXML
        private void onAergia() {
                if (shop.buyAergia()) {
                        lblAergiaMsg.setText("Select a point on a wire to apply effect.");
                        Audio.get().playSfx(AudioAssets.PURCHASE);

                        // Start selection mode in MainController
                        mainController.aergiaSelectionMode = true;

                        // Close the shop immediately
                        btnAergia.getScene().getWindow().hide();
                } else {
                        lblAergiaMsg.setText("Not enough coins (need 10) or on cooldown.");
                        Audio.get().playSfx(AudioAssets.ERROR);
                }
                refreshCoins();
        }

        @FXML
        private void onSisyphus() {
                if (shop.getState().sisyphusCooldown > 0) {
                        lblSisyphusMsg.setText("Wait " + (int)shop.getState().sisyphusCooldown + "s");
                        Audio.get().playSfx(AudioAssets.ERROR);
                } else if (engine.getCoins() < GameBalance.COST_SISYPHUS) {
                        lblSisyphusMsg.setText("Not enough coins (need 15).");
                        Audio.get().playSfx(AudioAssets.ERROR);
                } else {
                        lblSisyphusMsg.setText("Select a system to move.");
                        Audio.get().playSfx(AudioAssets.PURCHASE);
                        mainController.setSisyphusSelectionMode(true);
                        btnSisyphus.getScene().getWindow().hide();
                }
                refreshCoins();
        }


        @FXML private void onClose() {
                clearStatus();
                Audio.get().playSfx(AudioAssets.CLICK);
                // window is closed by the caller’s stage.close()/hide (we don't control here)
                closeBtn.getScene().getWindow().hide();
        }

        private void refreshCoins() {
                if (coinsInfo != null && engine != null) {
                        coinsInfo.setText("Your coins: " + engine.getCoins());
                }
        }
        private void clearStatus() {
                if (lblAtarMsg != null) lblAtarMsg.setText("");
                if (lblAiryMsg != null) lblAiryMsg.setText("");
                if (lblAnaMsg != null) lblAnaMsg.setText("");
        }
}
