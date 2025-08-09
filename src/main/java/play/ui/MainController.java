package play.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import play.audio.AudioManager;
import play.components.Seed;
import play.components.Transform;
import play.events.DeliveryListener;
import play.level.LevelLoader;
import play.system.*;

import java.io.IOException;
import java.util.List;

import play.core.Entity;

public class MainController {

        @FXML private Canvas gameCanvas;
        @FXML private AnchorPane canvasContainer;
        @FXML private Label entitiesLabel, seedsLabel, coinsLabel, remainingWireLabel, packetLossLabel, timeLabel;
        @FXML private Button shopButton, startButton;
        @FXML private Slider timeSlider;

        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;
        private AnimationTimer anim;
        private double gameTimeSeconds = 0.0;
        private boolean isJumpingToTime = false;
        private double targetTimeFromSlider = 0.0;
        private double timeScaleFactor = 1.0;
        private double totalWire = 5000.0;
        private double remainingWire = 5000.0;
        private int coins = 0;
        private boolean gameStarted = false;

        @FXML
        private void initialize() {
                // Load a level; fall back to factory if JSON not present
                try {
                        LevelLoader.loadFromResource("/levels/demo-level.json", engine);
                } catch (Exception ex) {
                        ex.printStackTrace();
                        play.level.LevelFactory.buildDemo(engine);
                }

                // Systems
                shopSystem = new ShopSystem(engine.entities());
                var prod = new ProductionSystem(engine, engine.entities(), 0.6); // spawn every 0.6s
                var move = new SeedMovementSystem(engine, engine.entities(), shopSystem);
                var collision = new CollisionSystem(engine, engine.entities(), shopSystem);

                // Register a wrapper to orchestrate update order
                engine.addSystem(dt -> {
                        shopSystem.update(dt);
                        prod.update(dt);
                        move.update(dt);
                        collision.update(dt);
                        // simple wire consumption: when links are created in LevelLoader you should deduct, but for demo we do nothing here
                });

                engine.setTickCallback(dt -> {
                        // render is performed on render() call scheduled from animation timer
                });

                // register delivery listener: award coins when seeds delivered
                engine.addDeliveryListener(new DeliveryListener() {
                        @Override
                        public void onSeedDelivered(Seed seed) {
                                int awarded = seed.type == Seed.Type.SQUARE ? 1 : 2;
                                Platform.runLater(() -> {
                                        addCoins(awarded);
                                        // play delivery sfx
                                        AudioManager.getInstance().playSfx("sfx/deliver.wav");
                                });
                        }
                });

                // set up animation timer with time-scale and time-jump behaviour
                anim = new AnimationTimer() {
                        private long prev = 0;
                        @Override
                        public void handle(long now) {
                                if (prev == 0) { prev = now; return; }
                                double dt = (now - prev) / 1e9;
                                prev = now;
                                double actualDt = dt * timeScaleFactor;
                                // cap big steps
                                if (actualDt > 0.2) actualDt = 0.2;
                                if (!gameStarted) return;
                                gameTimeSeconds += actualDt;
                                engine.tick(actualDt);

                                // if time-jump mode and reached targetTimeFromSlider:
                                if (isJumpingToTime && gameTimeSeconds >= targetTimeFromSlider) {
                                        gameTimeSeconds = targetTimeFromSlider;
                                        timeScaleFactor = 1.0;
                                        isJumpingToTime = false;
                                }

                                render();
                                updateHud();
                                checkEndConditions();
                        }
                };

                // hud & controls
                timeSlider.setOnMouseReleased(e -> handleTimeSliderChange());
                shopButton.setOnAction(e -> openShop());
                startButton.setOnAction(e -> startGame());

                // start background music
                AudioManager.getInstance().playBackground("/music/background.mp3", true);

                updateHud();
        }

        private void render() {
                GraphicsContext g = gameCanvas.getGraphicsContext2D();
                g.setFill(Color.web("#0d0d1a"));
                g.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
                for (Entity en : engine.entities()) {
                        if (!en.has(Transform.class)) continue;
                        Transform t = en.get(Transform.class);
                        if (en.has(Seed.class)) {
                                Seed s = en.get(Seed.class);
                                g.setFill(s.type == Seed.Type.SQUARE ? Color.CORNFLOWERBLUE : Color.HOTPINK);
                                g.fillOval(t.x - 6, t.y - 6, 12, 12);
                        } else if (en.has(play.components.PortInfo.class)) {
                                play.components.PortInfo p = en.get(play.components.PortInfo.class);
                                if (p.shape == play.components.PortInfo.Shape.SQUARE) {
                                        g.setFill(Color.LIGHTBLUE);
                                        g.fillRect(t.x - 6, t.y - 6, 12, 12);
                                } else {
                                        g.setFill(Color.LIGHTPINK);
                                        g.fillPolygon(new double[]{t.x, t.x + 6, t.x - 6}, new double[]{t.y + 6, t.y - 6, t.y - 6}, 3);
                                }
                        } else {
                                g.setFill(Color.DARKGRAY);
                                g.fillRect(t.x - 18, t.y - 12, 36, 24);
                        }
                }
        }

        private void updateHud() {
                // Do UI updates on FX thread
                Platform.runLater(() -> {
                        entitiesLabel.setText("Entities: " + engine.entities().size());
                        long seeds = engine.entities().stream().filter(e -> e.has(play.components.Seed.class)).count();
                        seedsLabel.setText("Seeds: " + seeds);
                        coinsLabel.setText("Coins: " + coins);

                        // wire values come from engine now
                        remainingWireLabel.setText(String.format("Wire Left: %.1f / %.1f",
                                engine.getRemainingWire(), engine.getTotalWire()));

                        int produced = engine.producedCount();
                        int lost = engine.lostCount();
                        String pct = produced == 0 ? "0%" : String.format("%d%%", (int) ((lost * 100.0) / produced));
                        packetLossLabel.setText(String.format("P.Loss/Total: %d/%d (%s)", lost, produced, pct));

                        // time display
                        int minutes = (int) (gameTimeSeconds / 60);
                        int seconds = (int) (gameTimeSeconds % 60);
                        timeLabel.setText(String.format("Time: %02d:%02d", minutes, seconds));

                        if (timeSlider.getMax() < 300) timeSlider.setMax(300);
                        if (!isJumpingToTime && !timeSlider.isValueChanging()) {
                                timeSlider.setValue(gameTimeSeconds);
                        }
                });
        }

        private void handleTimeSliderChange() {
                if (!gameStarted) return;
                if (isJumpingToTime) return;

                targetTimeFromSlider = timeSlider.getValue();
                if (targetTimeFromSlider <= gameTimeSeconds) {
                        // backward jump: reset everything and replay to target (simple approach)
                        resetForTimeJump(targetTimeFromSlider);
                        return;
                }
                // forward jump: speed up simulation to reach target quickly
                isJumpingToTime = true;
                timeScaleFactor = 10.0;
        }

        private void resetForTimeJump(double target) {
                // reset game state (simple but effective): clear entities and reload level, set gameTimeSeconds to 0 and then fast-forward
                engine.entities().clear();
                engine.resetStats();
                try {
                        LevelLoader.loadFromResource("/levels/demo-level.json", engine);
                } catch (Exception e) {
                        play.level.LevelFactory.buildDemo(engine);
                }
                gameTimeSeconds = 0;
                isJumpingToTime = true;
                timeScaleFactor = 50.0; // very fast to jump forward
                this.targetTimeFromSlider = target;
        }

        private void openShop() {
                // pause engine while shop open
                boolean wasRunning = gameStarted;
                if (anim != null) anim.stop();

                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/shop.fxml"));
                        Stage stage = new Stage();
                        stage.initOwner(canvasContainer.getScene().getWindow());
                        stage.initModality(Modality.APPLICATION_MODAL);
                        stage.setScene(new javafx.scene.Scene(loader.load()));
                        play.ui.ShopController ctrl = loader.getController();
                        ctrl.setDependencies(shopSystem, this);
                        stage.showAndWait();
                } catch (IOException e) {
                        e.printStackTrace();
                } finally {
                        // resume if game was running
                        if (wasRunning && anim != null) anim.start();
                }
        }

        private void startGame() {
                if (!gameStarted) {
                        gameStarted = true;
                        engine.resetStats();
                        gameTimeSeconds = 0;
                        anim.start();
                        startButton.setDisable(true);
                }
        }

        private void checkEndConditions() {
                // conditions:
                int produced = engine.producedCount();
                int lost = engine.lostCount();
                int reachedRef = engine.reachedReferenceCount();

                boolean timeUp = gameTimeSeconds >= 300.0; // game length (you can tune)
                boolean loss = produced > 0 && ((double) lost / produced) > 0.5;
                // success if production stops (we don't have explicit reference production timer here) but we approximate:
                boolean success = produced > 0 && ((double) reachedRef / produced) > 0.5 && gameTimeSeconds > 60.0;

                if (loss || (timeUp && !success)) {
                        gameStarted = false;
                        if (anim != null) anim.stop();
                        AudioManager.getInstance().playSfx("sfx/gameend.wav");
                        showEndDialog(loss ? "GAME OVER - HIGH PACKET LOSS!" : "GAME OVER - TIME'S UP");
                } else if (success) {
                        gameStarted = false;
                        if (anim != null) anim.stop();
                        AudioManager.getInstance().playSfx("sfx/gamewin.wav");
                        showEndDialog("LEVEL CLEARED!");
                }
        }

        private void showEndDialog(String message) {
                Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
                        a.setHeaderText(null);
                        a.initOwner(canvasContainer.getScene().getWindow());
                        a.showAndWait();
                        // Reset UI to allow new play
                        startButton.setDisable(false);
                });
        }

        // coins API for ShopController
        public boolean chargeCoins(int cost) {
                if (coins >= cost) { coins -= cost; updateHud(); return true; }
                return false;
        }
        public void addCoins(int n) { coins += n; updateHud(); }

        private void updateHud(boolean immediate) {
                if (immediate) updateHud();
        }

}
