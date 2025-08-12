package play.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import play.model.engine.GameEngine;
import play.model.systems.ShopSystem;

public class ShopController {

        // --- match Shop.fxml ids
        @FXML private Button btnAtar;
        @FXML private Button btnAiryaman;
        @FXML private Button btnAnahita;
        @FXML private Button closeBtn;
        @FXML private Label  lblAtarMsg;
        @FXML private Label  lblAiryMsg;
        @FXML private Label  lblAnaMsg;
        @FXML private Label  coinsInfo;

        // --- Core refs injected by caller ---
        private GameEngine engine;
        private ShopSystem shop;

        public void init(GameEngine engine, ShopSystem shop) {
                this.engine = engine;
                this.shop = shop;
                refreshCoins();
                clearStatus();
        }

        @FXML
        private void initialize() {
                // engine/shop arrive via init()
        }

        @FXML
        private void onAtar() {
                if (shop.buyAtar()) {
                        lblAtarMsg.setText("Impact waves disabled for 10s.");
                } else {
                        lblAtarMsg.setText("Not enough coins (need 3).");
                }
                refreshCoins();
        }

        @FXML
        private void onAiryaman() {
                if (shop.buyAiryaman()) {
                        lblAiryMsg.setText("Collisions disabled for 5s.");
                } else {
                        lblAiryMsg.setText("Not enough coins (need 4).");
                }
                refreshCoins();
        }

        @FXML
        private void onAnahita() {
                if (shop.buyAnahita()) {
                        lblAnaMsg.setText("All packet noise reset.");
                } else {
                        lblAnaMsg.setText("Not enough coins (need " + ShopSystem.COST_ANAHITA + ").");
                }
                refreshCoins();
        }

        @FXML
        private void onClose() {
                ((javafx.stage.Stage) closeBtn.getScene().getWindow()).close();
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
