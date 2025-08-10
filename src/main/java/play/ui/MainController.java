package play.ui;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
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

        // Core engine & systems
        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;
        private ProductionSystem productionSystem;
        private QueueSystem queueSystem;
        private RoutingSystem routingSystem;
        private SeedMovementSystem movementSystem;
        private CollisionSystem collisionSystem;

        // Wiring state
        private boolean wiringMode = false;   // hold W to wire
        private Entity dragStartPort = null;  // OUT port entity
        private double dragX, dragY;

        // Time control state
        private boolean forwardPressed = false;
        private boolean backwardPressed = false;
        private double timeScale = 1.0;       // 1.0 normal, >1 faster, <1 slower
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

        /**
         * Called by GameNavigator when entering the game scene.
         * If levelPath is null, defaults to /levels/level1.json
         */
        public void initLevel(String levelPath) {
                LevelLoaderV2.Loaded loaded = LevelLoaderV2.loadFromResource(
                        engine,
                        levelPath != null ? levelPath : "/levels/level1.json"
                );
                totalWire = loaded.totalWire;

                // Systems
                shopSystem = new ShopSystem(engine);
                productionSystem = new ProductionSystem(engine, engine.entities(), 0.2);
                queueSystem = new QueueSystem(engine, engine.entities());
                routingSystem = new RoutingSystem(engine, engine.entities());
                movementSystem = new SeedMovementSystem(engine, engine.entities(), shopSystem);
                collisionSystem = new CollisionSystem(engine, engine.entities(), shopSystem);

                engine.addSystem(shopSystem);
                engine.addSystem(productionSystem);
                engine.addSystem(queueSystem);
                engine.addSystem(movementSystem);
                engine.addSystem(collisionSystem);
                engine.addSystem(routingSystem);

                setupUiHooks();
                startLoop();
        }

        @FXML
        private void initialize() {
                // Key handlers after Scene is ready; remove listener once installed
                ChangeListener<Object> sceneReady = new ChangeListener<>() {
                        @Override public void changed(javafx.beans.value.ObservableValue<?> obs, Object o, Object n) {
                                if (gameCanvas.getScene() == null) return;

                                gameCanvas.getScene().setOnKeyPressed(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = true;
                                        if (c == KeyCode.LEFT)  backwardPressed = true;
                                        if (c == KeyCode.W)     wiringMode = true;
                                        if (c == KeyCode.S)     openShop();
                                });
                                gameCanvas.getScene().setOnKeyReleased(e -> {
                                        KeyCode c = e.getCode();
                                        if (c == KeyCode.RIGHT) forwardPressed = false;
                                        if (c == KeyCode.LEFT)  backwardPressed = false;
                                        if (c == KeyCode.W)     wiringMode = false;
                                });

                                // remove this listener; we’re wired up
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

                // Wiring mouse handlers (active only when wiringMode == true)
                gameCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
                gameCanvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        }

        private void startLoop() {
                loop = new AnimationTimer() {
                        @Override public void handle(long now) {
                                if (prevNanos == 0L) prevNanos = now;
                                double dt = (now - prevNanos) / 1_000_000_000.0;
                                prevNanos = now;

                                // "Both cancel" rule
                                if (forwardPressed && backwardPressed) timeScale = 1.0;
                                else if (forwardPressed)               timeScale = FAST;
                                else if (backwardPressed)              timeScale = SLOW;
                                else                                   timeScale = 1.0;

                                if (running) {
                                        double step = 1.0 / 60.0;
                                        double acc = dt * timeScale;
                                        while (acc >= step) {
                                                engine.tick(step);
                                                elapsed += step;
                                                acc -= step;
                                        }
                                }

                                draw();
                                updateHud();
                                updateStartEnabled();
                        }
                };
                loop.start();
        }

        private void openShop() {
                boolean wasRunning = running;
                running = false; // pause sim
                ShopViewHelper.showShop(engine, shopSystem);
                running = wasRunning; // resume
                gameCanvas.requestFocus();
        }

        private void toggleRun() {
                running = !running;
                startButton.setText(running ? "Pause" : "Start");
                gameCanvas.requestFocus();
        }

        private void fastForward(double seconds) {
                // forward-only jump (engine isn’t reversible)
                double step = 1.0 / 120.0;
                double t = Math.max(0, seconds);
                while (t > 0) {
                        double c = Math.min(step, t);
                        engine.tick(c);
                        elapsed += c;
                        t -= c;
                }
        }

        // ========= Wiring (mouse) =========
        private void onMousePressed(MouseEvent e) {
                if (!wiringMode) return;
                Entity port = findPortAt(e.getX(), e.getY());
                if (port == null) return;
                PortInfo p = port.get(PortInfo.class);
                if (p.io != PortInfo.IO.OUT) return; // start from OUT only
                dragStartPort = port;
                dragX = e.getX();
                dragY = e.getY();
        }

        private void onMouseDragged(MouseEvent e) {
                if (!wiringMode || dragStartPort == null) return;
                dragX = e.getX();
                dragY = e.getY();
        }

        private void onMouseReleased(MouseEvent e) {
                if (!wiringMode || dragStartPort == null) return;
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
                if (hasOutgoingLink(fromPort) || hasIncomingLink(toPort)) return false;

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

        private Entity findPortAt(double x, double y) {
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        Transform t = e.get(Transform.class);
                        if (Math.abs(x - t.x) <= 10 && Math.abs(y - t.y) <= 10) return e;
                }
                return null;
        }

        // ========= HUD / gating =========
        private void updateHud() {
                remainingWireLabel.setText(String.format("Wire Left: %.0f", Math.max(0, totalWire - usedWire)));
                entitiesLabel.setText("Entities: " + engine.entities().size());

                int seedCount = 0;
                for (Entity e : engine.entities()) if (e.has(Seed.class)) seedCount++;
                seedsLabel.setText("Seeds: " + seedCount);

                int loss = engine.getLostCount();
                int produced = engine.getProducedCount();
                double lossPct = produced == 0 ? 0 : (100.0 * loss / produced);
                packetLossLabel.setText(String.format("P.Loss/Total: %d/%d (%.0f%%)", loss, produced, lossPct));

                coinsLabel.setText("Coins: " + engine.getCoins());

                int secs = (int)Math.floor(elapsed);
                int m = secs / 60, s = secs % 60;
                timeLabel.setText(String.format("Time: %02d:%02d", m, s));
        }

        private void updateStartEnabled() {
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
                // Nodes = systems (entities with Transform but no PortInfo)
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

        // ========= Rendering =========
        private void draw() {
                GraphicsContext g = gameCanvas.getGraphicsContext2D();
                g.setFill(Color.web("#12161c"));
                g.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

                // grid
                g.setStroke(Color.web("#1b222b"));
                g.setLineWidth(1);
                for (int x = 0; x < gameCanvas.getWidth(); x += 20) g.strokeLine(x, 0, x, gameCanvas.getHeight());
                for (int y = 0; y < gameCanvas.getHeight(); y += 20) g.strokeLine(0, y, gameCanvas.getWidth(), y);

                // links
                g.setStroke(Color.GRAY);
                g.setLineWidth(2);
                for (Entity e : engine.entities()) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        if (l.fromPort == null || l.toPort == null) continue;
                        if (!l.fromPort.has(Transform.class) || !l.toPort.has(Transform.class)) continue;
                        Transform a = l.fromPort.get(Transform.class);
                        Transform b = l.toPort.get(Transform.class);
                        g.strokeLine(a.x, a.y, b.x, b.y);
                }

                // wiring preview (only when holding W)
                if (wiringMode && dragStartPort != null) {
                        Transform a = dragStartPort.get(Transform.class);
                        g.setStroke(Color.YELLOWGREEN);
                        g.strokeLine(a.x, a.y, dragX, dragY);
                }

                // systems (rectangle + indicator)
                for (Entity e : engine.entities()) {
                        if (e.has(PortInfo.class)) continue;
                        if (!e.has(Transform.class)) continue;
                        Transform t = e.get(Transform.class);
                        double w = 80, h = 80;

                        g.setFill(Color.web("#2a2f3a"));
                        g.fillRoundRect(t.x - w/2, t.y - h/2, w, h, 8, 8);
                        g.setStroke(Color.web("#3c4452"));
                        g.strokeRoundRect(t.x - w/2, t.y - h/2, w, h, 8, 8);

                        boolean on = systemPortsFilled(e);
                        g.setFill(on ? Color.LIME : Color.web("#403f3f"));
                        g.fillRoundRect(t.x - 10, t.y - h/2 - 12, 20, 6, 3, 3);

                        if (e.has(Reference.class)) {
                                g.setStroke(Color.LIGHTGREEN);
                                g.strokeOval(t.x - 20, t.y - 20, 40, 40);
                        }
                }

                // ports
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        Transform t = e.get(Transform.class);
                        boolean wired = (p.io == PortInfo.IO.OUT ? hasOutgoingLink(e) : hasIncomingLink(e));

                        if (p.shape == PortInfo.Shape.SQUARE) {
                                g.setFill(wired ? Color.web("#5dc2ff") : Color.web("#9ad9ff"));
                                g.fillRect(t.x - 6, t.y - 6, 12, 12);
                                g.setStroke(Color.web("#1f6aa5"));
                                g.strokeRect(t.x - 6, t.y - 6, 12, 12);
                        } else {
                                g.setFill(wired ? Color.web("#ff86a5") : Color.web("#ffc1d0"));
                                double[] xs = {t.x - 6, t.x + 6, t.x};
                                double[] ys = {t.y + 6, t.y + 6, t.y - 6};
                                g.fillPolygon(xs, ys, 3);
                                g.setStroke(Color.web("#a53d4e"));
                                g.strokePolygon(xs, ys, 3);
                        }
                }

                // seeds
                for (Entity e : engine.entities()) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);
                        if (s.type == Seed.Type.SQUARE) {
                                g.setFill(Color.BLUEVIOLET);
                                g.fillRect(t.x - 10, t.y - 10, 20, 20);
                        } else {
                                g.setFill(Color.HOTPINK);
                                double[] xs = {t.x - 10, t.x + 10, t.x};
                                double[] ys = {t.y + 10, t.y + 10, t.y - 10};
                                g.fillPolygon(xs, ys, 3);
                        }
                }
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
