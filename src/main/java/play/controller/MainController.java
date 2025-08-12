package play.controller;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import play.infrastructure.audio.Audio;
import play.model.audio.AudioAssets;
import play.model.components.*;
import play.model.core.Entity;
import play.model.engine.GameEngine;
import play.model.services.GameService;
import play.model.services.LevelRepository;
import play.model.services.WiringService;
import play.model.systems.*;
import play.render.RenderSystem;
import play.render.HudSystem;
import play.view.UiConstants;

import java.util.*;
import java.util.stream.Collectors;

public class MainController {

        @FXML private Label remainingWireLabel;
        @FXML private Label entitiesLabel;
        @FXML private Label seedsLabel;
        @FXML private Label packetLossLabel;
        @FXML private Label coinsLabel;
        @FXML private Label timeLabel;
        @FXML private Button shopButton;
        @FXML private Button startButton;
        @FXML private Slider timeSlider;
        @FXML private Canvas gameCanvas;
        @FXML private AnchorPane canvasContainer;

        private final GameService game = new GameService();
        private final LevelRepository levels = new LevelRepository();

        private GameEngine engine;
        private ShopSystem shopSystem;
        private ProductionSystem productionSystem;
        private QueueSystem queueSystem;
        private SeedMovementSystem movementSystem;
        private CollisionSystem collisionSystem;

        private RenderSystem renderSystem;
        private HudSystem hudSystem;

        private String currentLevelPath = "/levels/level1.json";
        private int timeLimitSeconds = 120;
        private double timeRemaining = 120.0;
        private boolean timeUpHandled = false;
        private boolean gameEnded = false;
        private boolean resultDialogQueued = false;

        private boolean wiringMode = false;
        private Entity dragStartPort = null;
        private double dragX, dragY;

        private boolean forwardPressed = false;
        private boolean backwardPressed = false;
        private double timeScale = 1.0;
        private static final double FAST = 3.0;
        private static final double SLOW = 0.25;

        private double totalWire = 3000;
        private double usedWire  = 0;

        private boolean running = false;
        private boolean hasStarted = false;

        private AnimationTimer loop;
        private long prevNanos = 0L;
        private double elapsed = 0.0;

        public void initLevel(String levelPath) {
                currentLevelPath = (levelPath != null) ? levelPath : "/levels/level1.json";

                game.loadLevel(currentLevelPath);
                engine = game.engine();

                usedWire = 0;
                timeUpHandled = false;
                gameEnded = false;
                resultDialogQueued = false;
                hasStarted = false;
                running = false;
                updateStartButtonLabel();
                updateStartButtonStyle();

                totalWire = game.totalWire();
                timeLimitSeconds = Math.max(10, game.timeLimitSeconds());
                timeRemaining = timeLimitSeconds;
                engine.setPlannedTotal(game.plannedSeeds());

                shopSystem       = new ShopSystem(engine);
                productionSystem = new ProductionSystem(engine, engine.entities(), 0.2);
                queueSystem      = new QueueSystem(engine, engine.entities());
                movementSystem   = new SeedMovementSystem(engine, engine.entities(), shopSystem, UiConstants.PORT_SIZE);
                // inject audio into collision system for gentle collision sfx (throttled)
                collisionSystem  = new CollisionSystem(engine, engine.entities(), shopSystem, UiConstants.PACKET_SIZE, Audio.get());

                engine.addSystem(shopSystem);
                engine.addSystem(productionSystem);
                engine.addSystem(queueSystem);
                engine.addSystem(movementSystem);
                engine.addSystem(collisionSystem);

                GraphicsContext gc = gameCanvas.getGraphicsContext2D();
                renderSystem = new RenderSystem(engine.entities(), gc);

                hudSystem = new HudSystem(
                        engine,
                        engine.entities(),
                        remainingWireLabel, entitiesLabel, seedsLabel, packetLossLabel, coinsLabel, timeLabel,
                        () -> totalWire,
                        () -> usedWire,
                        () -> timeRemaining
                );

                setupUiHooks();
                startLoop();
        }

        @FXML
        private void initialize() {
                if (canvasContainer != null) {
                        gameCanvas.widthProperty().bind(canvasContainer.widthProperty());
                        gameCanvas.heightProperty().bind(canvasContainer.heightProperty());
                }

                ChangeListener<Object> sceneReady = new ChangeListener<>() {
                        @Override public void changed(javafx.beans.value.ObservableValue<?> obs, Object o, Object n) {
                                if (gameCanvas.getScene() == null) return;

                                gameCanvas.getScene().setOnKeyPressed(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = true;
                                        if (c == KeyCode.LEFT)  backwardPressed = true;
                                        if (!running && !hasStarted && c == KeyCode.W) wiringMode = true;
                                        if (c == KeyCode.S)     openShop();
                                });
                                gameCanvas.getScene().setOnKeyReleased(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = false;
                                        if (c == KeyCode.LEFT)  backwardPressed = false;
                                        if (c == KeyCode.W)     wiringMode = false;
                                });

                                gameCanvas.sceneProperty().removeListener(this);
                        }
                };
                gameCanvas.sceneProperty().addListener(sceneReady);

                updateStartButtonLabel();
                updateStartButtonStyle();
        }

        private void setupUiHooks() {
                shopButton.setOnAction(evt -> {
                        Audio.get().playSfx(AudioAssets.CLICK);
                        openShop();
                });
                startButton.setOnAction(evt -> {
                        Audio.get().playSfx(AudioAssets.CLICK);
                        toggleRun();
                });
                timeSlider.valueProperty().addListener((obs, o, nv) -> {
                        fastForward(nv.doubleValue());
                        timeSlider.setValue(0);
                });

                gameCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, this::onMousePressed);
                gameCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
                gameCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        }

        private void startLoop() {
                if (loop != null) loop.stop();
                prevNanos = 0L;

                loop = new AnimationTimer() {
                        @Override public void handle(long now) {
                                if (prevNanos == 0L) prevNanos = now;
                                double dt = (now - prevNanos) / 1_000_000_000.0;
                                prevNanos = now;

                                if (forwardPressed && backwardPressed) timeScale = 1.0;
                                else if (forwardPressed)               timeScale = FAST;
                                else if (backwardPressed)              timeScale = SLOW;
                                else                                   timeScale = 1.0;

                                if (running && !gameEnded) {
                                        double step = 1.0 / 120.0;
                                        double acc = dt * timeScale;
                                        int maxSubSteps = 8;
                                        int sub = 0;
                                        while (acc >= step && sub < maxSubSteps) {
                                                engine.tick(step);
                                                elapsed += step;
                                                acc -= step;
                                                sub++;
                                        }

                                        timeRemaining -= dt * timeScale;
                                        if (timeRemaining <= 0 && !timeUpHandled) {
                                                timeRemaining = 0;
                                                handleTimeUp();
                                        }

                                        checkWinLose();
                                }

                                if (renderSystem != null) renderSystem.update(0);
                                if (hudSystem != null) hudSystem.update(0);

                                updateStartEnabled();
                        }
                };
                loop.start();
        }

        private void openShop() {
                boolean wasRunning = running;
                running = false;

                Stage owner = (Stage) gameCanvas.getScene().getWindow();
                ShopViewHelper.showShop(owner, engine, shopSystem, () -> {
                        running = wasRunning;
                        gameCanvas.requestFocus();
                        updateStartButtonLabel();
                        updateStartButtonStyle();
                });
        }

        private void toggleRun() {
                if (gameEnded) return;
                running = !running;
                if (running) {
                        hasStarted = true;
                        wiringMode = false;
                }
                updateStartButtonLabel();
                updateStartButtonStyle();
                gameCanvas.requestFocus();
        }

        private void fastForward(double seconds) {
                if (gameEnded) return;
                double step = 1.0 / 120.0;
                double t = Math.max(0, seconds);
                while (t > 0) {
                        double c = Math.min(step, t);
                        engine.tick(c);
                        elapsed += c;
                        t -= c;
                }
        }

        private void handleTimeUp() {
                timeUpHandled = true;

                int remainingQuotas = 0;
                for (Entity e : engine.entities()) {
                        if (!e.has(Producer.class)) continue;
                        Producer p = e.get(Producer.class);
                        int rem = Math.max(0, p.remainingSquare) + Math.max(0, p.remainingTriangle);
                        if (rem > 0) {
                                remainingQuotas += rem;
                                p.remainingSquare = 0;
                                p.remainingTriangle = 0;
                        }
                }
                if (remainingQuotas > 0) engine.incrementLostBy(remainingQuotas);

                int inFlight = 0;
                List<Entity> toRemove = new ArrayList<>();
                for (Entity e : engine.entities()) {
                        if (!e.has(Seed.class)) continue;
                        toRemove.add(e);
                        inFlight++;
                }
                if (inFlight > 0) {
                        engine.incrementLostBy(inFlight);
                        engine.entities().removeAll(toRemove);
                }

                checkWinLose();
                if (!gameEnded) {
                        int planned = engine.getPlannedTotal();
                        if (planned > 0) {
                                boolean win = (engine.getLostCount() * 2) < planned;
                                endGame(win);
                        } else {
                                endGame(true);
                        }
                }
        }

        private void checkWinLose() {
                if (gameEnded) return;
                int planned = engine.getPlannedTotal();
                if (planned <= 0) return;

                if (engine.getLostCount() * 2 >= planned) { endGame(false); return; }
                if (engine.getDeliveredCount() * 2 >= planned) { endGame(true); }
        }

        private void endGame(boolean win) {
                if (gameEnded) return;
                gameEnded = true;
                running = false;
                hasStarted = false;
                updateStartButtonLabel();
                updateStartButtonStyle();

                // Swap BGM based on result
                Audio.get().playBackground(win ? AudioAssets.VICTORY : AudioAssets.GAMEOVER, false);

                if (!resultDialogQueued) {
                        resultDialogQueued = true;
                        javafx.application.Platform.runLater(() -> showResultDialogNonBlocking(win));
                }
        }

        private void showResultDialogNonBlocking(boolean win) {
                String title = win ? "Stage Cleared!" : "Game Over";
                String header = win ? "Congratulations!" : "You Lost";
                int planned = engine.getPlannedTotal();
                int produced = engine.getProducedCount();
                int delivered = engine.getDeliveredCount();
                int lost = engine.getLostCount();
                int alive = Math.max(0, produced - delivered - lost);
                int coins = engine.getCoins();

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(header);
                alert.setContentText(
                        "Planned: " + planned +
                                "\nProduced: " + produced +
                                "\nDelivered: " + delivered +
                                "\nLost: " + lost +
                                "\nIn-Flight: " + alive +
                                "\nCoins earned: " + coins
                );

                ButtonType restart = new ButtonType("Restart Level", ButtonBar.ButtonData.OK_DONE);
                ButtonType mainMenu = new ButtonType("Main Menu", ButtonBar.ButtonData.CANCEL_CLOSE);
                ButtonType next = null;

                String nextPath = new LevelRepository().next(currentLevelPath);
                if (win && nextPath != null) {
                        next = new ButtonType("Next Level", ButtonBar.ButtonData.NEXT_FORWARD);
                        alert.getButtonTypes().setAll(restart, next, mainMenu);
                } else {
                        alert.getButtonTypes().setAll(restart, mainMenu);
                }

                try { alert.initOwner(gameCanvas.getScene().getWindow()); } catch (Exception ignore) {}

                final ButtonType finalNext = next;
                alert.setOnHidden(ev -> {
                        ButtonType res = alert.getResult();
                        if (res == restart) {
                                initLevel(currentLevelPath);
                                Audio.get().playBackground(AudioAssets.GAMEPLAY, true);
                        } else if (finalNext != null && res == finalNext) {
                                initLevel(nextPath);
                                Audio.get().playBackground(AudioAssets.GAMEPLAY, true);
                        } else {
                                try { GameNavigator.showMainMenu((Stage) gameCanvas.getScene().getWindow()); } catch (Exception ignore) {}
                        }
                        resultDialogQueued = false;
                });

                alert.show();
        }

        // ----- Wiring (pre-start only) -----
        private void onMousePressed(MouseEvent e) {
                if (!wiringMode || running || hasStarted) return;
                Entity port = findPortAt(e.getX(), e.getY());
                if (port == null) return;
                PortInfo p = port.get(PortInfo.class);
                if (p.io != PortInfo.IO.OUT) return;
                dragStartPort = port;
                dragX = e.getX(); dragY = e.getY();
                if (renderSystem != null) renderSystem.updateWiringPreview(dragStartPort, dragX, dragY, true);
        }
        private void onMouseDragged(MouseEvent e) {
                if (!wiringMode || running || hasStarted || dragStartPort == null) return;
                dragX = e.getX(); dragY = e.getY();
                if (renderSystem != null) renderSystem.updateWiringPreview(dragStartPort, dragX, dragY, true);
        }
        private void onMouseReleased(MouseEvent e) {
                if (!wiringMode || running || hasStarted || dragStartPort == null) return;
                Entity target = findPortAt(e.getX(), e.getY());
                boolean ok = false;
                if (target != null) ok = tryCreateLink(dragStartPort, target);
                dragStartPort = null;
                if (renderSystem != null) renderSystem.updateWiringPreview(null, 0, 0, false);

                // SFX feedback
                Audio.get().playSfx(ok ? AudioAssets.WIRE_CONNECT : AudioAssets.ERROR);
        }

        private boolean tryCreateLink(Entity fromPort, Entity toPort) {
                WiringService.WiringComputation comp = WiringService.compute(engine.entities(), fromPort, toPort);
                if (!comp.valid) return false;

                double netInc = comp.netIncrease();
                if (usedWire + netInc > totalWire) return false;

                WiringService.performRewire(engine.entities(), fromPort, toPort);
                usedWire += netInc;
                if (usedWire < 0) usedWire = 0;
                return true;
        }

        private Entity findPortAt(double x, double y) {
                final double half = UiConstants.PORT_SIZE / 2.0;
                final double pad  = 2.0;
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        Transform t = e.get(Transform.class);
                        if (Math.abs(x - t.x) <= half + pad && Math.abs(y - t.y) <= half + pad) return e;
                }
                return null;
        }

        private void updateStartEnabled() {
                if (gameEnded) { startButton.setDisable(true); return; }
                if (running)    { startButton.setDisable(false); return; }
                if (hasStarted) { startButton.setDisable(false); return; }
                boolean allFilled = WiringService.allPortsFilled(engine.entities());
                boolean connected = WiringService.isGraphConnected(engine.entities());
                startButton.setDisable(!(allFilled && connected));
        }

        private void updateStartButtonLabel() {
                if (running) startButton.setText("Pause");
                else if (hasStarted) startButton.setText("Resume");
                else startButton.setText("Start");
        }
        private void updateStartButtonStyle() {
                if (!startButton.getStyleClass().contains("primary")) startButton.getStyleClass().add("primary");
                startButton.getStyleClass().remove("resume");
                if (!running && hasStarted) startButton.getStyleClass().add("resume");
        }

        static final class ShopViewHelper {
                static void showShop(Stage owner, GameEngine engine, ShopSystem shop, Runnable onClosed) {
                        try {
                                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                                        MainController.class.getResource("/fxml/Shop.fxml")
                                );
                                javafx.scene.Parent root = loader.load();
                                play.controller.ShopController ctrl = loader.getController();
                                ctrl.init(engine, shop);

                                javafx.stage.Stage stage = new javafx.stage.Stage();
                                javafx.scene.Scene scene = new javafx.scene.Scene(root);
                                scene.getStylesheets().addAll(
                                        MainController.class.getResource("/css/theme.css").toExternalForm(),
                                        MainController.class.getResource("/css/shop.css").toExternalForm()
                                );
                                stage.setScene(scene);
                                stage.setTitle("Shop");
                                stage.setResizable(false);

                                if (owner != null) {
                                        stage.initOwner(owner);
                                        stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                                } else {
                                        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                                }

                                stage.setOnHidden(ev -> { if (onClosed != null) onClosed.run(); });
                                stage.show();
                        } catch (Exception ex) {
                                ex.printStackTrace();
                                if (onClosed != null) onClosed.run();
                        }
                }
        }
}
