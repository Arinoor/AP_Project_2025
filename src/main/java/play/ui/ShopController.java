package play.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import play.system.ShopSystem;

public class ShopController {

        @FXML private Button btnAtar, btnAiryaman, btnAnahita, closeBtn;
        @FXML private Label coinsInfo, lblAtarMsg, lblAiryMsg, lblAnaMsg;

        private ShopSystem shop;
        private MainController main;

        public void setDependencies(ShopSystem shopSystem, MainController mainController) {
                this.shop = shopSystem;
                this.main = mainController;
                coinsInfo.setText("Your coins: 0"); // we can request actual coins from main if you expose getter
        }

        @FXML private void initialize() {
                btnAtar.setOnAction(e -> buyAtar());
                btnAiryaman.setOnAction(e -> buyAiryaman());
                btnAnahita.setOnAction(e -> buyAnahita());
                closeBtn.setOnAction(e -> ((Stage) closeBtn.getScene().getWindow()).close());
        }

        private void buyAtar() {
                if (main.chargeCoins(3)) {
                        double now = System.currentTimeMillis() / 1000.0;
                        shop.purchaseAtar(3, now);
                        lblAtarMsg.setText("Activated for 10s");
                } else lblAtarMsg.setText("Not enough coins");
        }

        private void buyAiryaman() {
                if (main.chargeCoins(4)) {
                        double now = System.currentTimeMillis() / 1000.0;
                        shop.purchaseAiryaman(4, now);
                        lblAiryMsg.setText("Activated for 5s");
                } else lblAiryMsg.setText("Not enough coins");
        }

        private void buyAnahita() {
                if (main.chargeCoins(5)) {
                        shop.purchaseAnahita(5);
                        lblAnaMsg.setText("Collision counters reset");
                } else lblAnaMsg.setText("Not enough coins");
        }
}
