package play.ui;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import play.components.Queue;
import play.core.Entity;
import play.components.*;
import play.level.LevelLoaderV2;
import play.system.*;

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

        // Level meta
        private String currentLevelPath = "/levels/level1.json";
        private int timeLimitSeconds = 120;
        private double timeRemaining = 120.0;
        private boolean timeUpHandled = false;
        private boolean gameEnded = false;
        private boolean resultDialogQueued = false; // NEW: prevent showAndWait during animation & double dialogs

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
                resultDialogQueued = false; // reset dialog guard

                LevelLoaderV2.Loaded loaded = LevelLoaderV2.loadFromResource(engine, currentLevelPath);
                totalWire = loaded.totalWire;
                timeLimitSeconds = Math.max(10, loaded.timeLimitSeconds);
                timeRemaining = timeLimitSeconds;
                engine.setPlannedTotal(loaded.plannedSeeds);

                // Ensure NO links initially
                engine.entities().removeIf(e -> e.has(Link.class));
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
                                        if (!running && c == KeyCode.W) wiringMode = true;     // wiring only when NOT running
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

                                draw();
                                updateHud();
                                updateStartEnabled(); // gating for Start button before run
                        }
                };
                loop.start();
        }

        // ----- Shop / Start -----
        private void openShop() {
                boolean was = running;
                running = false;
                ShopViewHelper.showShop(engine, shopSystem);
                running = was;
                gameCanvas.requestFocus();
        }

        private void toggleRun() {
                if (gameEnded) return;
                running = !running;
                // Wiring only allowed when NOT running
                if (running) wiringMode = false;
                startButton.setText(running ? "Pause" : "Start");
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
                checkWinLose(); // will call end if threshold crossed
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

                // set owner so dialog stays on top of the game window
                try {
                        alert.initOwner(gameCanvas.getScene().getWindow());
                } catch (Exception ignore) {}

                Optional<ButtonType> res = alert.showAndWait(); // now safe (we're no longer inside AnimationTimer pulse)
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
                if (!wiringMode || running) return;
                Entity port = findPortAt(e.getX(), e.getY());
                if (port == null) return;
                PortInfo p = port.get(PortInfo.class);
                if (p.io != PortInfo.IO.OUT) return; // must start from OUT
                dragStartPort = port;
                dragX = e.getX();
                dragY = e.getY();
        }

        private void onMouseDragged(MouseEvent e) {
                if (!wiringMode || running || dragStartPort == null) return;
                dragX = e.getX();
                dragY = e.getY();
        }

        private void onMouseReleased(MouseEvent e) {
                if (!wiringMode || running || dragStartPort == null) return;
                Entity target = findPortAt(e.getX(), e.getY());
                if (target != null) tryCreateLink(dragStartPort, target);
                dragStartPort = null;
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
                if (usedWire + wireLen > totalWire) return false;

                Entity linkE = new Entity().add(new Link(fromPort, toPort));
                engine.entities().add(linkE);
                usedWire += wireLen;
                if (!toPort.has(Queue.class)) toPort.add(new Queue(5));
                return true;
        }

        private void removeOutgoingLinks(Entity fromPort) {
                List<Entity> toRemove = new ArrayList<>();
                for (Entity e : engine.entities()) {
                        if (!e.has(Link.class)) continue;
                        if (e.get(Link.class).fromPort == fromPort) toRemove.add(e);
                }
                subtractWireForLinks(toRemove);
                engine.entities().removeAll(toRemove);
        }

        private void removeIncomingLinks(Entity toPort) {
                List<Entity> toRemove = new ArrayList<>();
                for (Entity e : engine.entities()) {
                        if (!e.has(Link.class)) continue;
                        if (e.get(Link.class).toPort == toPort) toRemove.add(e);
                }
                subtractWireForLinks(toRemove);
                engine.entities().removeAll(toRemove);
        }

        private void subtractWireForLinks(List<Entity> links) {
                for (Entity e : links) {
                        Link l = e.get(Link.class);
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
                        && !e.has(Link.class);
        }

        // ----- HUD / gating -----
        private void updateHud() {
                remainingWireLabel.setText(String.format("Wire Left: %.0f", Math.max(0, totalWire - usedWire)));
                entitiesLabel.setText("Entities: " + engine.entities().size());

                int seedCount = 0;
                for (Entity e : engine.entities()) if (e.has(Seed.class)) seedCount++;
                seedsLabel.setText("Seeds: " + seedCount);

                int loss = engine.getLostCount();
                double lossPct = (engine.getPlannedTotal() == 0) ? 0
                        : (100.0 * loss / engine.getPlannedTotal());
                packetLossLabel.setText(String.format("Loss: %d/%d (%.0f%%)",
                        loss, engine.getPlannedTotal(), lossPct));

                coinsLabel.setText("Coins: " + engine.getCoins());

                int secs = (int)Math.ceil(timeRemaining);
                int m = Math.max(0, secs / 60), s = Math.max(0, secs % 60);
                timeLabel.setText(String.format("Time: %02d:%02d", m, s));
        }

        private void updateStartEnabled() {
                if (running || gameEnded) {
                        startButton.setDisable(false);
                        return;
                }
                boolean allFilled = allPortsFilled();
                boolean connected = isGraphConnected();
                startButton.setDisable(!(allFilled && connected));
        }

        private boolean allPortsFilled() {
                Map<Entity, Integer> outDeg = new HashMap<>();
                Map<Entity, Integer> inDeg  = new HashMap<>();
                for (Entity e : engine.entities()) if (e.has(Link.class)) {
                        Link l = e.get(Link.class);
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
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
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

        // ----- Rendering -----
        private void draw() {
                GraphicsContext g = gameCanvas.getGraphicsContext2D();

                // background
                g.setFill(Color.web("#12161c"));
                g.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

                // grid
                g.setStroke(Color.web("#1b222b"));
                g.setLineWidth(1);
                for (int x = 0; x < gameCanvas.getWidth(); x += 20) g.strokeLine(x, 0, x, gameCanvas.getHeight());
                for (int y = 0; y < gameCanvas.getHeight(); y += 20) g.strokeLine(0, y, gameCanvas.getWidth(), y);

                // links (colored by shape)
                for (Entity e : engine.entities()) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (l.fromPort == null || l.toPort == null) continue;
                        if (!l.fromPort.has(Transform.class) || !l.toPort.has(Transform.class)) continue;

                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);

                        PortInfo.Shape shape = l.fromPort.get(PortInfo.class).shape;
                        Color linkColor = (shape == PortInfo.Shape.SQUARE)
                                ? Color.web("#5dc2ff")
                                : Color.web("#ff86a5");

                        g.setStroke(linkColor);
                        g.setLineWidth(2.0);
                        g.strokeLine(a.x, a.y, b.x, b.y);
                }

                // wiring preview (hold W; only when not running)
                if (wiringMode && !running && dragStartPort != null) {
                        Transform a = dragStartPort.get(Transform.class);
                        g.setStroke(Color.YELLOWGREEN);
                        g.setLineWidth(2.0);
                        g.strokeLine(a.x, a.y, dragX, dragY);
                }

                // systems (rectangle + indicator; Reference ring)
                for (Entity e : engine.entities()) {
                        if (!isSystemEntity(e)) continue;

                        Transform t = e.get(Transform.class);
                        double w = SYSTEM_SIZE, h = SYSTEM_SIZE;

                        g.setFill(Color.web("#2a2f3a"));
                        g.fillRoundRect(t.x - w/2, t.y - h/2, w, h, 8, 8);
                        g.setStroke(Color.web("#3c4452"));
                        g.strokeRoundRect(t.x - w/2, t.y - h/2, w, h, 8, 8);

                        boolean on = systemPortsFilled(e);
                        g.setFill(on ? Color.LIME : Color.web("#403f3f"));
                        g.fillRoundRect(t.x - 10, t.y - h/2 - 12, 20, 6, 3, 3);

                        if (e.has(Reference.class)) {
                                g.setStroke(Color.LIGHTGREEN);
                                g.strokeOval(t.x - 22, t.y - 22, 44, 44);
                        }
                }

                // ports
                final double ps = UiConstants.PORT_SIZE;
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        Transform t = e.get(Transform.class);
                        boolean wired = hasLinkFor(p, e);

                        if (p.shape == PortInfo.Shape.SQUARE) {
                                g.setFill(wired ? Color.web("#5dc2ff") : Color.web("#9ad9ff"));
                                g.fillRect(t.x - ps/2, t.y - ps/2, ps, ps);
                                g.setStroke(Color.web("#1f6aa5"));
                                g.strokeRect(t.x - ps/2, t.y - ps/2, ps, ps);
                        } else {
                                g.setFill(wired ? Color.web("#ff86a5") : Color.web("#ffc1d0"));
                                double[] xs = {t.x - ps/2, t.x + ps/2, t.x};
                                double[] ys = {t.y + ps/2, t.y + ps/2, t.y - ps/2};
                                g.fillPolygon(xs, ys, 3);
                                g.setStroke(Color.web("#a53d4e"));
                                g.strokePolygon(xs, ys, 3);
                        }
                }

                // packets: render via PacketView
                for (Entity e : engine.entities()) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);
                        PacketView.render(g, s, t.x, t.y);
                }
        }

        private boolean hasLinkFor(PortInfo p, Entity e) {
                if (p.io == PortInfo.IO.OUT) return hasOutgoingLink(e);
                else                         return hasIncomingLink(e);
        }

        private boolean hasOutgoingLink(Entity fromPort) {
                for (Entity e : engine.entities())
                        if (e.has(Link.class) && e.get(Link.class).fromPort == fromPort) return true;
                return false;
        }

        private boolean hasIncomingLink(Entity toPort) {
                for (Entity e : engine.entities())
                        if (e.has(Link.class) && e.get(Link.class).toPort == toPort) return true;
                return false;
        }

        private boolean systemPortsFilled(Entity system) {
                boolean ok = true;
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        if (p.parentSystem != system) continue;
                        if (p.io == PortInfo.IO.OUT) ok &= hasOutgoingLink(e);
                        else                         ok &= hasIncomingLink(e);
                        if (!ok) return false;
                }
                return true;
        }

        /** Tiny helper to show the Shop modal with injected engine/shop. */
        private static final class ShopViewHelper {
                static void showShop(GameEngine engine, ShopSystem shop) {
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
                                stage.showAndWait();
                        } catch (Exception ex) {
                                ex.printStackTrace();
                        }
                }
        }
}
