package play.controller;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import play.infrastructure.audio.Audio;
import play.model.audio.AudioAssets;
import play.model.components.*;
import play.model.constants.GameBalance;
import play.model.core.Entity;
import play.model.engine.GameEngine;
import play.model.level.LevelLoaderV2;
import play.model.systems.CollisionSystem;
import play.model.systems.ProductionSystem;
import play.model.systems.QueueSystem;
import play.model.systems.SeedMovementSystem;
import play.model.systems.ShopSystem;
import play.render.HudSystem;
import play.render.RenderSystem;
import play.utils.WiringUtils;
import play.view.UiConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        private GameEngine engine;

        // Systems
        private ShopSystem shopSystem;
        private ProductionSystem productionSystem;
        private QueueSystem queueSystem;
        private SeedMovementSystem movementSystem;
        private CollisionSystem collisionSystem;

        private RenderSystem renderSystem;
        private HudSystem hudSystem;

        // Level state
        private String currentLevelPath = "/levels/level1.json";
        private double totalWire = 3000;
        private double usedWire  = 0;
        private int timeLimitSeconds = 120;
        private double timeRemaining = 120.0;

        // Persist coins across levels (carry between engine instances)
        private int carryCoins = 0;

        // Game state
        private boolean running = false;
        private boolean hasStarted = false;
        private boolean wasRunningBeforeShop = false;
        private boolean timeUpHandled = false;
        private boolean gameEnded = false;
        private boolean resultDialogQueued = false;

        // Input/timeflow
        private boolean forwardPressed = false;
        private boolean backwardPressed = false;
        private double timeScale = 1.0;
        private static final double FAST = 3.0;
        private static final double SLOW = 0.25;

        // Wiring (phase 1)
        private boolean wiringMode = false;
        private Entity dragStartPort = null;
        private double dragX, dragY, dragStartX, dragStartY;

        // Bend tool (phase 2)
        private BendTool bendTool;

        // Loop
        private AnimationTimer loop;
        private long prevNanos = 0L;
        private double elapsed = 0.0;


        // Aergia item
        public boolean aergiaSelectionMode = false;
        private Link selectedLinkForAergia = null;
        private double selectedPositionForAergia = 0.0;

        // Sisyphus item
        private boolean sisyphusSelectionMode = false;
        private Entity selectedSystemForSisyphus = null;
        private double originalSystemX, originalSystemY;
        private Map<Entity, double[]> originalPortPositions = new HashMap<>();

        // ---------- Lifecycle ----------

        @FXML
        private void initialize() {
                if (canvasContainer != null) {
                        gameCanvas.widthProperty().bind(canvasContainer.widthProperty());
                        gameCanvas.heightProperty().bind(canvasContainer.heightProperty());
                }

                // Keys
                ChangeListener<Object> sceneReady = new ChangeListener<>() {
                        @Override public void changed(javafx.beans.value.ObservableValue<?> obs, Object o, Object n) {
                                if (gameCanvas.getScene() == null) return;

                                gameCanvas.getScene().setOnKeyPressed(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = true;
                                        if (c == KeyCode.LEFT)  backwardPressed = true;
                                        if (!running && !hasStarted && c == KeyCode.W) wiringMode = true;
                                        if (c == KeyCode.S) openShop();

                                        // Add this block to handle cancellation
                                        if (c == KeyCode.ESCAPE && (aergiaSelectionMode || sisyphusSelectionMode)) {
                                                aergiaSelectionMode = false;
                                                sisyphusSelectionMode = false;
                                                running = wasRunningBeforeShop;
                                                Audio.get().playSfx(AudioAssets.CLICK);
                                        }
                                });
                                gameCanvas.getScene().setOnKeyReleased(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = false;
                                        if (c == KeyCode.LEFT)  backwardPressed = false;
                                        if (c == KeyCode.W)     wiringMode = false;
                                });

                                gameCanvas.getScene().getAccelerators().clear();
                                gameCanvas.sceneProperty().removeListener(this);
                        }
                };
                gameCanvas.sceneProperty().addListener(sceneReady);

                // Buttons
                shopButton.setOnAction(e -> {
                        Audio.get().playSfx(AudioAssets.CLICK);
                        openShop();
                });
                startButton.setOnAction(e -> {
                        Audio.get().playSfx(AudioAssets.CLICK);
                        toggleRun();
                });
                timeSlider.valueProperty().addListener((obs, o, nv) -> {
                        fastForward(nv.doubleValue());
                        timeSlider.setValue(0);
                });

                // Canvas mouse handlers (phase-1 wiring)
                gameCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, this::onMousePressed);
                gameCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
                gameCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::onMouseClicked);


                updateStartButtonLabel();
                updateStartButtonStyle();

                // First level
                initLevel("/levels/level1.json");
        }

        public void initLevel(String levelPath) {
                currentLevelPath = (levelPath != null) ? levelPath : "/levels/level1.json";

                // --- Preserve coins BEFORE swapping the engine ---
                if (engine != null) {
                        carryCoins = engine.getCoins();
                }

                // Engine fresh
                engine = new GameEngine();

                // Load entities (systems + ports) from JSON
                LevelLoaderV2.Loaded loaded = LevelLoaderV2.loadFromResource(engine, currentLevelPath);
                totalWire = loaded.totalWire;
                timeLimitSeconds = loaded.timeLimitSeconds;
                timeRemaining = timeLimitSeconds;
                engine.setPlannedTotal(loaded.plannedSeeds);

                // --- Restore carried coins on the new engine ---
                if (carryCoins > 0) {
                        engine.incrementCoins(carryCoins);
                }

                // Reset bends for new level
                WiringUtils.clearAllBends();

                // Counters/state
                usedWire = WiringService.totalWireLength(engine.entities());
                timeUpHandled = false;
                gameEnded = false;
                resultDialogQueued = false;
                hasStarted = false;
                running = false;
                updateStartButtonLabel();
                updateStartButtonStyle();

                // Systems wiring
                shopSystem       = new ShopSystem(engine);
                productionSystem = new ProductionSystem(engine, engine.entities(), 0.2);
                queueSystem      = new QueueSystem(engine, engine.entities());
                movementSystem   = new SeedMovementSystem(engine, engine.entities(), shopSystem, UiConstants.PORT_SIZE);
                collisionSystem  = new CollisionSystem(engine, engine.entities(), shopSystem, UiConstants.PACKET_SIZE, Audio.get());

                engine.addSystem(shopSystem);
                engine.addSystem(productionSystem);
                engine.addSystem(queueSystem);
                engine.addSystem(movementSystem);
                engine.addSystem(collisionSystem);

                // Render + HUD
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

                // Bend tool (attach to canvas) â€” use three functional params
                bendTool = new BendTool(
                        engine,
                        engine.entities(),
                        UiConstants.SYSTEM_SIZE,
                        () -> totalWire,
                        () -> usedWire,
                        (nw) -> usedWire = nw
                );
                bendTool.attach(gameCanvas);

                // Let RenderSystem draw bend hover ring
                renderSystem.setBendTool(bendTool);

                // Loop
                startLoop();
        }

        private void startLoop() {
                if (loop != null) loop.stop();
                prevNanos = 0L;

                loop = new AnimationTimer() {
                        @Override public void handle(long now) {
                                if (prevNanos == 0L) prevNanos = now;
                                double dt = (now - prevNanos) / 1_000_000_000.0;
                                prevNanos = now;

                                // Time scale
                                if (forwardPressed && backwardPressed) timeScale = 1.0;
                                else if (forwardPressed)               timeScale = FAST;
                                else if (backwardPressed)              timeScale = SLOW;
                                else                                   timeScale = 1.0;

                                // Simulation
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

                                // Draw + HUD
                                if (renderSystem != null) renderSystem.update(0);
                                if (hudSystem != null) hudSystem.update(0);

                                // Enable/disable Start
                                updateStartEnabled();
                        }
                };
                loop.start();
        }

        // ---------- UI actions (unchanged) ----------


        private void openShop() {
                wasRunningBeforeShop = running;
                running = false;

                Stage owner = (Stage) gameCanvas.getScene().getWindow();
                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Shop.fxml"));
                        Parent root = loader.load();
                        ShopController ctrl = loader.getController();
                        ctrl.init(this, engine, shopSystem); // Pass this MainController

                        Stage stage = new Stage();
                        Scene scene = new Scene(root);
                        try {
                                scene.getStylesheets().addAll(
                                        getClass().getResource("/css/theme.css").toExternalForm(),
                                        getClass().getResource("/css/shop.css").toExternalForm()
                                );
                        } catch (Exception ignore) {}

                        stage.setScene(scene);
                        stage.setTitle("Shop");
                        stage.setResizable(false);
                        if (owner != null) {
                                stage.initOwner(owner);
                                stage.initModality(Modality.WINDOW_MODAL);
                        } else {
                                stage.initModality(Modality.APPLICATION_MODAL);
                        }
                        stage.setOnHidden(ev -> {
                                if (!aergiaSelectionMode && !sisyphusSelectionMode) {
                                        running = wasRunningBeforeShop;
                                }
                                gameCanvas.requestFocus();
                                updateStartButtonLabel();
                                updateStartButtonStyle();
                        });
                        stage.show();
                } catch (Exception ex) {
                        ex.printStackTrace();
                        running = wasRunningBeforeShop;
                }
        }


        private void toggleRun() {
                if (gameEnded) return;
                running = !running;
                if (running) {
                        hasStarted = true;
                        wiringMode = false; // exit wiring mode on start
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

        // ---------- Results / checks / end game (unchanged) ----------

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
                        boolean win = (planned <= 0) || (engine.getLostCount() * 2) < planned;
                        endGame(win);
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
                alert.getButtonTypes().setAll(restart, mainMenu);

                try { alert.initOwner(gameCanvas.getScene().getWindow()); } catch (Exception ignore) {}

                alert.setOnHidden(ev -> {
                        ButtonType res = alert.getResult();
                        if (res == restart) {
                                initLevel(currentLevelPath);
                                Audio.get().playBackground(AudioAssets.GAMEPLAY, true);
                        } else {
                                try { GameNavigator.showMainMenu((Stage) gameCanvas.getScene().getWindow()); } catch (Exception ignore) {}
                        }
                        resultDialogQueued = false;
                });

                alert.show();
        }

        // ---------- Wiring & Bends (unchanged) ----------

        private void onMousePressed(MouseEvent e) {
                if (running) return;

                // BendTool owns SHIFT interactions
                if (e.isShiftDown()) return;

                if (sisyphusSelectionMode) {
                        Entity system = findSystemAt(e.getX(), e.getY());
                        if (system != null && !system.has(Reference.class)) {
                                selectedSystemForSisyphus = system;
                                dragStartX = e.getX();
                                dragStartY = e.getY();
                                Transform t = system.get(Transform.class);
                                originalSystemX = t.x;
                                originalSystemY = t.y;

                                // Store original port positions
                                originalPortPositions.clear();
                                for (Entity entity : engine.entities()) {
                                        if (entity.has(PortInfo.class)) {
                                                PortInfo portInfo = entity.get(PortInfo.class);
                                                if (portInfo.parentSystem == system) {
                                                        Transform portTransform = entity.get(Transform.class);
                                                        originalPortPositions.put(entity, new double[]{portTransform.x, portTransform.y});
                                                }
                                        }
                                }
                        }
                        return;
                }

                // Phase 1 wiring: only when W is held and run hasn't started yet
                if (!wiringMode || hasStarted) return;
                Entity port = findPortAt(e.getX(), e.getY());
                if (port == null) return;
                PortInfo p = port.get(PortInfo.class);
                if (p.io != PortInfo.IO.OUT) return;

                dragStartPort = port;
                dragX = e.getX(); dragY = e.getY();
                if (renderSystem != null) renderSystem.updateWiringPreview(dragStartPort, dragX, dragY, true);
        }

        private void onMouseDragged(MouseEvent e) {
                if (running) return;


                if (sisyphusSelectionMode && selectedSystemForSisyphus != null) {
                        double dx = e.getX() - dragStartX;
                        double dy = e.getY() - dragStartY;
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        if (distance > UiConstants.SISYPHUS_MAX_RADIUS) {
                                dx = dx * UiConstants.SISYPHUS_MAX_RADIUS / distance;
                                dy = dy * UiConstants.SISYPHUS_MAX_RADIUS / distance;
                        }
                        Transform systemTransform = selectedSystemForSisyphus.get(Transform.class);
                        systemTransform.x = originalSystemX + dx;
                        systemTransform.y = originalSystemY + dy;

                        // Move all ports of this system by the same displacement
                        for (Entity port : originalPortPositions.keySet()) {
                                double[] orig = originalPortPositions.get(port);
                                Transform portTransform = port.get(Transform.class);
                                portTransform.x = orig[0] + dx;
                                portTransform.y = orig[1] + dy;
                        }

                        // Update queued packets to match their port positions
                        queueSystem.updateSystemPosition(selectedSystemForSisyphus, systemTransform.x, systemTransform.y);

                        // Update seed positions on affected links
                        updateSeedPositionsForMovedSystem(selectedSystemForSisyphus, dx, dy);
                        return;
                }

                if (!wiringMode || hasStarted || dragStartPort == null) return;

                dragX = e.getX(); dragY = e.getY();
                if (renderSystem != null) renderSystem.updateWiringPreview(dragStartPort, dragX, dragY, true);
        }

        private void onMouseReleased(MouseEvent e) {
                if (running) return;

                if (sisyphusSelectionMode && selectedSystemForSisyphus != null) {
                        boolean valid = validateSystemMove(selectedSystemForSisyphus);
                        if (valid) {
                                usedWire = WiringUtils.totalWireLength(engine.entities());
                                // Final update to ensure all positions are correct
                                queueSystem.updateSystemPosition(selectedSystemForSisyphus,
                                        selectedSystemForSisyphus.get(Transform.class).x,
                                        selectedSystemForSisyphus.get(Transform.class).y);
                                // Deduct coins only after successful move
                                engine.incrementCoins(-GameBalance.COST_SISYPHUS);
                                shopSystem.getState().sisyphusCooldown = 1.0;
                        } else {
                                // Revert system and ports to original positions
                                Transform systemTransform = selectedSystemForSisyphus.get(Transform.class);
                                systemTransform.x = originalSystemX;
                                systemTransform.y = originalSystemY;
                                for (Entity port : originalPortPositions.keySet()) {
                                        double[] orig = originalPortPositions.get(port);
                                        Transform portTransform = port.get(Transform.class);
                                        portTransform.x = orig[0];
                                        portTransform.y = orig[1];
                                }
                                // Also revert queued packets
                                queueSystem.updateSystemPosition(selectedSystemForSisyphus, originalSystemX, originalSystemY);
                                Audio.get().playSfx(AudioAssets.ERROR);
                        }
                        selectedSystemForSisyphus = null;
                        originalPortPositions.clear();
                        sisyphusSelectionMode = false;
                        running = wasRunningBeforeShop;
                }

                // BendTool owns SHIFT interactions
                if (e.isShiftDown()) return;

                if (!wiringMode || hasStarted || dragStartPort == null) return;

                Entity target = findPortAt(e.getX(), e.getY());
                boolean ok = false;
                if (target != null) ok = tryCreateLink(dragStartPort, target);

                dragStartPort = null;
                if (renderSystem != null) renderSystem.updateWiringPreview(null, 0, 0, false);
                Audio.get().playSfx(ok ? AudioAssets.WIRE_CONNECT : AudioAssets.ERROR);
        }


        private void onMouseClicked(MouseEvent e) {
                if (!aergiaSelectionMode) return;

                aergiaSelectionMode = false;
                shopSystem.getState().aergiaSelectionActive = false;

                // Find nearest link segment
                WiringUtils.SegmentHit hit = WiringUtils.findNearestSegment(
                        engine.entities(), e.getX(), e.getY(), 10.0);

                if (hit != null) {
                        // Calculate normalized position along the link
                        double totalLength = WiringUtils.pathLength(hit.link);
                        List<WiringUtils.Pt> path = WiringUtils.path(hit.link);

                        // Calculate length up to the hit segment
                        double lengthToSegment = 0.0;
                        for (int i = 0; i < hit.segmentIndex; i++) {
                                WiringUtils.Pt a = path.get(i);
                                WiringUtils.Pt b = path.get(i+1);
                                lengthToSegment += Math.hypot(b.x - a.x, b.y - a.y);
                        }

                        // Calculate length within the segment
                        WiringUtils.Pt segStart = path.get(hit.segmentIndex);
                        WiringUtils.Pt segEnd = path.get(hit.segmentIndex+1);
                        double segmentLength = Math.hypot(segEnd.x - segStart.x, segEnd.y - segStart.y);
                        double hitDistance = Math.hypot(hit.hitX - segStart.x, hit.hitY - segStart.y);

                        double normalizedPosition = (lengthToSegment + hitDistance) / totalLength;

                        // Create Aergia effect entity with correct position
                        Entity effect = new Entity().add(new AergiaEffect(
                                hit.link,
                                normalizedPosition,
                                GameBalance.AERGIA_DURATION
                        ));
                        engine.entities().add(effect);

                        // Deduct coins and set cooldown
                        engine.incrementCoins(-GameBalance.COST_AERGIA);
                        shopSystem.getState().aergiaCooldown = GameBalance.AERGIA_COOLDOWN;

                        Audio.get().playSfx(AudioAssets.PURCHASE);
                } else {
                        Audio.get().playSfx(AudioAssets.ERROR);
                }

                running = wasRunningBeforeShop;
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
                boolean anyCross  = WiringService.hasAnySystemCrossing(engine.entities(), UiConstants.SYSTEM_SIZE);
                boolean withinBudget = (usedWire <= totalWire);

                startButton.setDisable(!(allFilled && connected && !anyCross && withinBudget));
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

        private Entity findSystemAt(double x, double y) {
                final double halfSize = UiConstants.SYSTEM_SIZE / 2.0;
                for (Entity e : engine.entities()) {
                        if (isSystemEntity(e) && e.has(Transform.class)) {
                                Transform t = e.get(Transform.class);
                                if (Math.abs(x - t.x) <= halfSize && Math.abs(y - t.y) <= halfSize) {
                                        return e;
                                }
                        }
                }
                return null;
        }

        private void updateSeedPositionsForMovedSystem(Entity movedSystem, double dx, double dy) {
                for (Entity entity : engine.entities()) {
                        if (entity.has(Link.class)) {
                                Link link = entity.get(Link.class);
                                Entity fromSystem = link.fromPort.get(PortInfo.class).parentSystem;
                                Entity toSystem = link.toPort.get(PortInfo.class).parentSystem;

                                if (fromSystem == movedSystem || toSystem == movedSystem) {
                                        for (Entity seedEntity : engine.entities()) {
                                                if (seedEntity.has(Seed.class)) {
                                                        Seed seed = seedEntity.get(Seed.class);
                                                        if (seed.currentLink == link && seedEntity.has(Transform.class)) {
                                                                // Recalculate position based on link geometry
                                                                WiringUtils.Pt pos = WiringUtils.pointAlongNormalized(
                                                                        link,
                                                                        seed.returning ? 1 - seed.progress : seed.progress
                                                                );
                                                                Transform seedTransform = seedEntity.get(Transform.class);
                                                                seedTransform.x = pos.x;
                                                                seedTransform.y = pos.y;
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }


        private boolean validateSystemMove(Entity movedSystem) {
                double newWireLength = WiringUtils.totalWireLength(engine.entities());
                if (newWireLength > totalWire) {
                        return false;
                }
                if (WiringUtils.hasAnySystemCrossing(engine.entities(), UiConstants.SYSTEM_SIZE)) {
                        return false;
                }
                return true;
        }


        private boolean isSystemEntity(Entity e) {
                return e.has(Transform.class) && !e.has(PortInfo.class) && !e.has(Seed.class) && !e.has(Link.class);
        }

        public void setSisyphusSelectionMode(boolean mode) {
                this.sisyphusSelectionMode = mode;
        }
}
