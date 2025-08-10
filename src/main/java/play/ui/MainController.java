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
import play.components.PortInfo;
import play.components.Reference;
import play.events.DeliveryListener;
import play.level.LevelLoader;
import play.level.LevelFactory;
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
        private int coins = 0;
        private boolean gameStarted = false;

        @FXML
        private void initialize() {
                // Load level (try JSON, otherwise build demo)
                try {
                        LevelLoader.loadFromResource("/levels/demo-level.json", engine);
                } catch (Exception ex) {
                        ex.printStackTrace();
                        LevelFactory.buildDemo(engine); // fallback demo builder
                }

                // Systems
                shopSystem = new ShopSystem(engine.entities());
                var prod  = new ProductionSystem(engine, engine.entities());
                var move  = new SeedMovementSystem(engine, engine.entities(), shopSystem);
                var collide = new CollisionSystem(engine, engine.entities(), shopSystem);
                var queue = new QueueSystem(engine.entities());

                engine.addSystem(dt -> {
                        prod.update(dt);     // spawn only if free
                        queue.update(dt);    // push queued seeds when outputs free
                        move.update(dt);     // move + coins + enqueue on arrival
                        collide.update(dt);  // detect collisions + AoE + losses
                        shopSystem.update(dt);
                });

                // Delivery listener: award coins and play sfx
                engine.addDeliveryListener(new DeliveryListener() {
                        @Override
                        public void onSeedDelivered(Seed seed) {
                                int awarded = seed.type == Seed.Type.SQUARE ? 1 : 2;
                                Platform.runLater(() -> {
                                        addCoins(awarded);
                                        AudioManager.getInstance().playSfx("/sfx/deliver.wav");
                                });
                        }
                });

                // Animation loop: respects timeScaleFactor and time-jump behavior
                anim = new AnimationTimer() {
                        private long prev = 0;
                        @Override
                        public void handle(long now) {
                                if (prev == 0) { prev = now; return; }
                                double dt = (now - prev) / 1e9;
                                prev = now;
                                double actualDt = dt * timeScaleFactor;
                                // clamp large timesteps
                                if (actualDt > 0.2) actualDt = 0.2;
                                if (!gameStarted) return;

                                gameTimeSeconds += actualDt;
                                engine.tick(actualDt);

                                // If we're fast-forwarding to a slider target, stop when reached
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

                // UI hooks
                timeSlider.setOnMouseReleased(e -> handleTimeSliderChange());
                shopButton.setOnAction(e -> openShop());
                startButton.setOnAction(e -> startGame());

                // Start background music (if available)
                AudioManager.getInstance().playBackground("/music/background.mp3", true);

                updateHud();
        }

        private void render() {
                GraphicsContext g = gameCanvas.getGraphicsContext2D();
                g.setFill(Color.web("#0d0d1a"));
                g.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

                // Render pattern: seeds, ports, and generic nodes
                for (Entity en : engine.entities()) {
                        if (!en.has(Transform.class)) continue;
                        Transform t = en.get(Transform.class);

                        if (en.has(Seed.class)) {
                                Seed s = en.get(Seed.class);
                                g.setFill(s.type == Seed.Type.SQUARE ? Color.CORNFLOWERBLUE : Color.HOTPINK);
                                g.fillOval(t.x - 6, t.y - 6, 12, 12);
                        } else if (en.has(PortInfo.class)) {
                                PortInfo p = en.get(PortInfo.class);
                                if (p.shape == PortInfo.Shape.SQUARE) {
                                        g.setFill(Color.LIGHTBLUE);
                                        g.fillRect(t.x - 6, t.y - 6, 12, 12);
                                } else {
                                        g.setFill(Color.LIGHTPINK);
                                        g.fillPolygon(new double[]{t.x, t.x + 6, t.x - 6}, new double[]{t.y + 6, t.y - 6, t.y - 6}, 3);
                                }
                                // draw indicator for reference ports
                                if (en.has(Reference.class)) {
                                        g.setStroke(Color.GOLD);
                                        g.strokeOval(t.x - 10, t.y - 10, 20, 20);
                                }
                        } else {
                                g.setFill(Color.DARKGRAY);
                                g.fillRect(t.x - 18, t.y - 12, 36, 24);
                        }
                }
        }

        private void updateHud() {
                Platform.runLater(() -> {
                        entitiesLabel.setText("Entities: " + engine.entities().size());
                        long seeds = engine.entities().stream().filter(e -> e.has(Seed.class)).count();
                        seedsLabel.setText("Seeds: " + seeds);
                        coinsLabel.setText("Coins: " + coins);

                        // Wire accounting from engine
                        remainingWireLabel.setText(String.format("Wire Left: %.1f / %.1f",
                                engine.getRemainingWire(), engine.getTotalWire()));

                        int produced = engine.producedCount();
                        int lost = engine.lostCount();
                        String pct = produced == 0 ? "0%" : String.format("%d%%", (int) ((lost * 100.0) / produced));
                        packetLossLabel.setText(String.format("P.Loss/Total: %d/%d (%s)", lost, produced, pct));

                        int minutes = (int) (gameTimeSeconds / 60);
                        int seconds = (int) (gameTimeSeconds % 60);
                        timeLabel.setText(String.format("Time: %02d:%02d", minutes, seconds));

                        // Slider behaviour: make sure max is reasonable and bind to current time unless user scrubs
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
                        // backward jump: reload level & fast-forward to target
                        resetForTimeJump(targetTimeFromSlider);
                        return;
                }
                // forward jump: speed up simulation until we reach target
                isJumpingToTime = true;
                timeScaleFactor = 10.0;
        }

        private void resetForTimeJump(double target) {
                // clear entities and reload level; then fast-forward
                engine.entities().clear();
                engine.resetStats();
                try {
                        LevelLoader.loadFromResource("/levels/demo-level.json", engine);
                } catch (Exception e) {
                        LevelFactory.buildDemo(engine);
                }
                gameTimeSeconds = 0;
                isJumpingToTime = true;
                timeScaleFactor = 50.0; // very fast to reach the target
                this.targetTimeFromSlider = target;
        }

        private void openShop() {
                // Pause game while shop is open
                boolean wasRunning = gameStarted;
                if (anim != null) anim.stop();
                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/shop.fxml"));
                        Stage stage = new Stage();
                        stage.initOwner(canvasContainer.getScene().getWindow());
                        stage.initModality(Modality.APPLICATION_MODAL);
                        stage.setScene(new javafx.scene.Scene(loader.load()));
                        ShopController ctrl = loader.getController();
                        ctrl.setDependencies(shopSystem, this);
                        stage.showAndWait();
                } catch (IOException e) {
                        e.printStackTrace();
                } finally {
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
                int produced = engine.producedCount();
                int lost = engine.lostCount();
                int reachedRef = engine.reachedReferenceCount();

                boolean timeUp = gameTimeSeconds >= 300.0;
                boolean loss = produced > 0 && ((double) lost / produced) > 0.5;
                boolean success = produced > 0 && ((double) reachedRef / produced) > 0.5 && gameTimeSeconds > 60.0;

                if (loss || (timeUp && !success)) {
                        gameStarted = false;
                        if (anim != null) anim.stop();
                        AudioManager.getInstance().playSfx("/sfx/gameend.wav");
                        showEndDialog(loss ? "GAME OVER - HIGH PACKET LOSS!" : "GAME OVER - TIME'S UP");
                } else if (success) {
                        gameStarted = false;
                        if (anim != null) anim.stop();
                        AudioManager.getInstance().playSfx("/sfx/gamewin.wav");
                        showEndDialog("LEVEL CLEARED!");
                }
        }

        private void showEndDialog(String message) {
                Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
                        a.setHeaderText(null);
                        a.initOwner(canvasContainer.getScene().getWindow());
                        a.showAndWait();
                        // allow player to restart
                        startButton.setDisable(false);
                });
        }

        // coins API for ShopController
        public boolean chargeCoins(int cost) {
                if (coins >= cost) { coins -= cost; updateHud(); return true; }
                return false;
        }
        public void addCoins(int n) { coins += n; updateHud(); }
}
