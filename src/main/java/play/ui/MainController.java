package play.ui;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import play.components.PortInfo;
import play.components.Producer;
import play.components.Queue;
import play.components.Reference;
import play.components.Seed;
import play.components.Transform;
import play.core.Entity;
import play.level.LevelLoaderV2;
import play.system.CollisionSystem;
import play.system.GameEngine;
import play.system.HudSystem;
import play.system.ProductionSystem;
import play.system.QueueSystem;
import play.system.RenderSystem;
import play.system.SeedMovementSystem;
import play.system.ShopSystem;

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

        // Engine & systems
        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;
        private ProductionSystem productionSystem;
        private QueueSystem queueSystem;
        private SeedMovementSystem movementSystem;
        private CollisionSystem collisionSystem;

        // NEW: rendering & HUD systems
        private RenderSystem renderSystem;
        private HudSystem hudSystem;

        // Level meta
        private String currentLevelPath = "/levels/level1.json";
        private int timeLimitSeconds = 120;
        private double timeRemaining = 120.0;
        private boolean timeUpHandled = false;
        private boolean gameEnded = false;
        private boolean resultDialogQueued = false;
        private boolean hasStarted = false;

        // Wiring interaction (hold W and drag from OUT to IN)
        private boolean wiringMode = false;
        private Entity dragStartPort = null;
        private double dragX, dragY;

        // Time control
        private boolean forwardPressed = false;
        private boolean backwardPressed = false;
        private double timeScale = 1.0;
        private static final double FAST = 3.0;
        private static final double SLOW = 0.25;

        // Wire budget
        private double totalWire = 3000;
        private double usedWire  = 0;

        // Loop timing
        private AnimationTimer loop;
        private long prevNanos = 0L;
        private double elapsed = 0.0;
        private boolean running = false;

        private static final double SYSTEM_SIZE = UiConstants.SYSTEM_SIZE;

        public void initLevel(String levelPath) {
                currentLevelPath = (levelPath != null) ? levelPath : "/levels/level1.json";

                // Full reset for restart
                engine.resetForLevel();
                usedWire = 0;
                timeUpHandled = false;
                gameEnded = false;
                resultDialogQueued = false;

                LevelLoaderV2.Loaded loaded = LevelLoaderV2.loadFromResource(engine, currentLevelPath);
                totalWire = loaded.totalWire;
                timeLimitSeconds = Math.max(10, loaded.timeLimitSeconds);
                timeRemaining = timeLimitSeconds;
                engine.setPlannedTotal(loaded.plannedSeeds);

                // Ensure NO links initially
                engine.entities().removeIf(e -> e.has(play.components.Link.class));
                usedWire = 0;

                // Systems (order matters): Production -> Queue -> Movement -> Collision
                shopSystem       = new ShopSystem(engine);
                productionSystem = new ProductionSystem(engine, engine.entities(), 0.2);
                queueSystem      = new QueueSystem(engine, engine.entities());
                movementSystem   = new SeedMovementSystem(engine, engine.entities(), shopSystem, UiConstants.PORT_SIZE);
                collisionSystem  = new CollisionSystem(engine, engine.entities(), shopSystem, UiConstants.PACKET_SIZE);

                engine.addSystem(shopSystem);
                engine.addSystem(productionSystem);
                engine.addSystem(queueSystem);
                engine.addSystem(movementSystem);
                engine.addSystem(collisionSystem);

                // NEW: Render system (entities & wiring preview)
                GraphicsContext gc = gameCanvas.getGraphicsContext2D();
                renderSystem = new RenderSystem(engine.entities(), gc);
                engine.addSystem(renderSystem);

                // NEW: HUD system (labels)
                hudSystem = new HudSystem(
                        engine,
                        engine.entities(),
                        remainingWireLabel, entitiesLabel, seedsLabel, packetLossLabel, coinsLabel, timeLabel,
                        () -> totalWire,
                        () -> usedWire,
                        () -> timeRemaining
                );
                engine.addSystem(hudSystem);

                setupUiHooks();
                startLoop();
        }

        @FXML
        private void initialize() {
                // Keyboard
                ChangeListener<Object> sceneReady = new ChangeListener<>() {
                        @Override public void changed(javafx.beans.value.ObservableValue<?> obs, Object o, Object n) {
                                if (gameCanvas.getScene() == null) return;

                                gameCanvas.getScene().setOnKeyPressed(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = true;
                                        if (c == KeyCode.LEFT)  backwardPressed = true;
                                        if (!running && !hasStarted && c == KeyCode.W) wiringMode = true; // <-- changed
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
        }

        private void setupUiHooks() {
                shopButton.setOnAction(evt -> openShop());
                startButton.setOnAction(evt -> toggleRun());
                timeSlider.valueProperty().addListener((obs, o, nv) -> {
                        fastForward(nv.doubleValue());
                        timeSlider.setValue(0);
                });

                gameCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
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
                                        // Advance simulation
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

                                        // Countdown (scaled with timeScale)
                                        timeRemaining -= dt * timeScale;
                                        if (timeRemaining <= 0 && !timeUpHandled) {
                                                timeRemaining = 0;
                                                handleTimeUp();
                                        }

                                        // Check win/lose continuously
                                        checkWinLose();
                                }

                                // Always render & refresh HUD even when paused
                                if (renderSystem != null) renderSystem.update(0);
                                if (hudSystem != null) hudSystem.update(0);

                                updateStartEnabled(); // gating for Start button before run
                        }
                };
                loop.start();
        }

        // ----- Shop / Start -----
        private void openShop() {
                boolean was = running;
                running = false;

                // pass the current window as owner
                Stage owner = (Stage) gameCanvas.getScene().getWindow();
                ShopViewHelper.showShop(owner, engine, shopSystem);

                running = was;
                gameCanvas.requestFocus();
        }


        private void toggleRun() {
                if (gameEnded) return;

                // If we're about to start for the first time, keep the existing gating behavior
                if (!hasStarted && !running) {
                        // (gating already handled by updateStartEnabled / Start disabled when not ready)
                }

                running = !running;

                if (running) {
                        hasStarted = true;   // entering RUNNING from either BUILDING or PAUSED
                        wiringMode = false;  // never wire while running
                }

                // Button label reflects state
                startButton.setText(running ? "Pause" : (hasStarted ? "Resume" : "Start"));
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

        // ----- Time up handling -----
        private void handleTimeUp() {
                timeUpHandled = true;

                // 1) Convert remaining producer quotas to "lost"
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

                // 2) Remove all in-flight packet entities and count as lost
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

                // 3) After time up, evaluate result now
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

                if (engine.getLostCount() * 2 >= planned) {
                        endGame(false);
                        return;
                }
                if (engine.getDeliveredCount() * 2 >= planned) {
                        endGame(true);
                }
        }

        private void endGame(boolean win) {
                if (gameEnded) return;
                gameEnded = true;
                running = false;
                startButton.setText("Start");

                // Defer dialog to next pulse to avoid "showAndWait during animation/layout"
                if (!resultDialogQueued) {
                        resultDialogQueued = true;
                        javafx.application.Platform.runLater(() -> showResultDialog(win));
                }
        }

        private void showResultDialog(boolean win) {
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

                String nextPath = getNextLevelPath(currentLevelPath);
                if (win && nextPath != null) {
                        next = new ButtonType("Next Level", ButtonBar.ButtonData.NEXT_FORWARD);
                        alert.getButtonTypes().setAll(restart, next, mainMenu);
                } else {
                        alert.getButtonTypes().setAll(restart, mainMenu);
                }

                try {
                        alert.initOwner(gameCanvas.getScene().getWindow());
                } catch (Exception ignore) {}

                Optional<ButtonType> res = alert.showAndWait();
                if (res.isPresent()) {
                        if (res.get() == restart) {
                                initLevel(currentLevelPath);
                        } else if (next != null && res.get() == next) {
                                initLevel(nextPath);
                        } else {
                                try {
                                        GameNavigator.showMainMenu((Stage) gameCanvas.getScene().getWindow());
                                } catch (Exception ignore) {}
                        }
                }
        }

        private String getNextLevelPath(String current) {
                if (current == null) return null;
                if (current.endsWith("level1.json")) return "/levels/level2.json";
                return null;
        }


        // ----- Wiring mouse handlers -----
        private void onMousePressed(MouseEvent e) {
                if (!wiringMode || running || hasStarted) return;
                Entity port = findPortAt(e.getX(), e.getY());
                if (port == null) return;
                PortInfo p = port.get(PortInfo.class);
                if (p.io != PortInfo.IO.OUT) return; // must start from OUT
                dragStartPort = port;
                dragX = e.getX();
                dragY = e.getY();
                if (renderSystem != null) renderSystem.updateWiringPreview(dragStartPort, dragX, dragY, true);
        }

        private void onMouseDragged(MouseEvent e) {
                if (!wiringMode || running || hasStarted || dragStartPort == null) return;
                dragX = e.getX();
                dragY = e.getY();
                if (renderSystem != null) renderSystem.updateWiringPreview(dragStartPort, dragX, dragY, true);
        }

        private void onMouseReleased(MouseEvent e) {
                if (!wiringMode || running || hasStarted || dragStartPort == null) return;
                Entity target = findPortAt(e.getX(), e.getY());
                if (target != null) tryCreateLink(dragStartPort, target);
                dragStartPort = null;
                if (renderSystem != null) renderSystem.updateWiringPreview(null, 0, 0, false);
        }

        private boolean tryCreateLink(Entity fromPort, Entity toPort) {
                if (!fromPort.has(PortInfo.class) || !toPort.has(PortInfo.class)) return false;
                PortInfo a = fromPort.get(PortInfo.class);
                PortInfo b = toPort.get(PortInfo.class);

                if (a.io != PortInfo.IO.OUT || b.io != PortInfo.IO.IN) return false;
                if (a.parentSystem == b.parentSystem) return false;
                if (a.shape != b.shape) return false;

                // replace any existing links from OUT or to IN (rewiring)
                removeOutgoingLinks(fromPort);
                removeIncomingLinks(toPort);

                Transform ta = fromPort.get(Transform.class);
                Transform tb = toPort.get(Transform.class);
                double wireLen = Math.hypot(tb.x - ta.x, tb.y - ta.y);
                if (usedWire + wireLen > totalWire) {
                        // (optional) could show a toast here
                        return false;
                }

                Entity linkE = new Entity().add(new play.components.Link(fromPort, toPort));
                engine.entities().add(linkE);
                usedWire += wireLen;
                return true;
        }

        private void removeOutgoingLinks(Entity fromPort) {
                List<Entity> toRemove = new ArrayList<>();
                for (Entity e : engine.entities()) {
                        if (!e.has(play.components.Link.class)) continue;
                        if (e.get(play.components.Link.class).fromPort == fromPort) toRemove.add(e);
                }
                subtractWireForLinks(toRemove);
                engine.entities().removeAll(toRemove);
        }

        private void removeIncomingLinks(Entity toPort) {
                List<Entity> toRemove = new ArrayList<>();
                for (Entity e : engine.entities()) {
                        if (!e.has(play.components.Link.class)) continue;
                        if (e.get(play.components.Link.class).toPort == toPort) toRemove.add(e);
                }
                subtractWireForLinks(toRemove);
                engine.entities().removeAll(toRemove);
        }

        private void subtractWireForLinks(List<Entity> links) {
                for (Entity e : links) {
                        play.components.Link l = e.get(play.components.Link.class);
                        if (l.fromPort != null && l.toPort != null &&
                                l.fromPort.has(Transform.class) && l.toPort.has(Transform.class)) {
                                Transform a = l.fromPort.get(Transform.class);
                                Transform b = l.toPort.get(Transform.class);
                                usedWire -= Math.hypot(b.x - a.x, b.y - a.y);
                        }
                }
                if (usedWire < 0) usedWire = 0;
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

        private boolean isSystemEntity(Entity e) {
                return e.has(Transform.class)
                        && !e.has(PortInfo.class)
                        && !e.has(Seed.class)
                        && !e.has(play.components.Link.class);
        }

        // ----- HUD / gating -----
        private void updateStartEnabled() {
                if (gameEnded) {
                        startButton.setDisable(true);
                        return;
                }
                if (running) {
                        startButton.setDisable(false); // can always pause
                        return;
                }
                if (hasStarted) {
                        startButton.setDisable(false); // PAUSED -> always allow resume
                        return;
                }
                // BUILDING: only allow Start when wiring is valid
                boolean allFilled = allPortsFilled();
                boolean connected = isGraphConnected();
                startButton.setDisable(!(allFilled && connected));
        }

        private boolean allPortsFilled() {
                Map<Entity, Integer> outDeg = new HashMap<>();
                Map<Entity, Integer> inDeg  = new HashMap<>();
                for (Entity e : engine.entities()) if (e.has(play.components.Link.class)) {
                        play.components.Link l = e.get(play.components.Link.class);
                        outDeg.merge(l.fromPort, 1, Integer::sum);
                        inDeg.merge(l.toPort, 1, Integer::sum);
                }
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        if (p.io == PortInfo.IO.OUT && outDeg.getOrDefault(e, 0) != 1) return false;
                        if (p.io == PortInfo.IO.IN  && inDeg.getOrDefault(e, 0)  != 1) return false;
                }
                return true;
        }

        private boolean isGraphConnected() {
                List<Entity> systems = engine.entities().stream()
                        .filter(e -> e.has(Transform.class) && !e.has(PortInfo.class))
                        .collect(Collectors.toList());
                if (systems.isEmpty()) return true;

                Map<Entity, Set<Entity>> adj = new HashMap<>();
                for (Entity s : systems) adj.put(s, new HashSet<>());
                for (Entity e : engine.entities()) {
                        if (!e.has(play.components.Link.class)) continue;
                        play.components.Link l = e.get(play.components.Link.class);
                        Entity A = l.fromPort.get(PortInfo.class).parentSystem;
                        Entity B = l.toPort.get(PortInfo.class).parentSystem;
                        adj.get(A).add(B);
                        adj.get(B).add(A);
                }

                Set<Entity> seen = new HashSet<>();
                Deque<Entity> dq = new ArrayDeque<>();
                dq.add(systems.get(0));
                seen.add(systems.get(0));
                while (!dq.isEmpty()) {
                        Entity u = dq.pollFirst();
                        for (Entity v : adj.getOrDefault(u, Set.of())) if (seen.add(v)) dq.add(v);
                }
                return seen.size() == systems.size();
        }

        /** Tiny helper to show the Shop modal with injected engine/shop. */
        private static final class ShopViewHelper {
                static void showShop(Stage owner, GameEngine engine, ShopSystem shop) {
                        try {
                                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                                        MainController.class.getResource("/fxml/Shop.fxml")
                                );
                                javafx.scene.Parent root = loader.load();
                                play.ui.ShopController ctrl = loader.getController();
                                ctrl.init(engine, shop);

                                javafx.stage.Stage stage = new javafx.stage.Stage();
                                stage.setScene(new javafx.scene.Scene(root));
                                stage.setTitle("Shop");
                                stage.setResizable(false);

                                // Proper modality + owner
                                if (owner != null) {
                                        stage.initOwner(owner);
                                        stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
                                } else {
                                        // fallback to app-modal if no owner
                                        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                                }

                                stage.showAndWait(); // blocks until Close
                        } catch (Exception ex) {
                                ex.printStackTrace();
                        }
                }
        }

}
