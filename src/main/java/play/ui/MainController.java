package play.ui;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import play.core.Entity;
import play.components.*;
import play.system.*;

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
        @FXML private AnchorPane canvasContainer;
        @FXML private Canvas gameCanvas;

        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;
        private ProductionSystem productionSystem;
        private QueueSystem queueSystem;
        private RoutingSystem routingSystem;
        private SeedMovementSystem movementSystem;
        private CollisionSystem collisionSystem;

        private AnimationTimer loop;
        private long prevNanos = 0L;
        private double elapsed = 0.0;
        private boolean running = false;

        @FXML
        private void initialize() {
                // Build a tiny sample level (same as console demo)
                buildSampleLevel(engine);

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

                // UI hooks
                shopButton.setOnAction(evt -> ShopViewHelper.showShop(engine, shopSystem));
                startButton.setOnAction(evt -> toggleRun());


                // Timer slider: jump (fast-forward) seconds
                timeSlider.valueProperty().addListener((obs, oldV, newV) -> fastForward(newV.doubleValue()));

                // Render loop
                loop = new AnimationTimer() {
                        @Override public void handle(long now) {
                                if (prevNanos == 0L) prevNanos = now;
                                double dt = (now - prevNanos) / 1_000_000_000.0;
                                prevNanos = now;

                                if (running) {
                                        // fixed-ish step to keep things stable
                                        double step = 1.0 / 60.0;
                                        double acc = dt;
                                        while (acc >= step) {
                                                engine.tick(step);
                                                elapsed += step;
                                                acc -= step;
                                        }
                                }
                                draw();
                                updateHud();
                        }
                };
                loop.start();
        }

        private void toggleRun() {
                running = !running;
                startButton.setText(running ? "Pause" : "Start");
        }

        private void fastForward(double seconds) {
                // advance logic without rendering
                double step = 1.0 / 120.0;
                double t = seconds;
                while (t > 0) {
                        double c = Math.min(step, t);
                        engine.tick(c);
                        elapsed += c;
                        t -= c;
                }
                timeSlider.setValue(0.0);
        }

        private void updateHud() {
                remainingWireLabel.setText("Wire Left: 0"); // not tracked in ECS version
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

        private void draw() {
                GraphicsContext g = gameCanvas.getGraphicsContext2D();
                g.setFill(Color.WHITE);
                g.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

                // Links
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

                // Ports
                for (Entity e : engine.entities()) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        Transform t = e.get(Transform.class);
                        if (p.shape == PortInfo.Shape.SQUARE) {
                                g.setFill(p.io == PortInfo.IO.OUT ? Color.LIGHTBLUE : Color.SKYBLUE);
                                g.fillRect(t.x - 6, t.y - 6, 12, 12);
                        } else {
                                g.setFill(p.io == PortInfo.IO.OUT ? Color.LIGHTPINK : Color.PEACHPUFF);
                                // simple triangle
                                double[] xs = {t.x - 6, t.x + 6, t.x};
                                double[] ys = {t.y + 6, t.y + 6, t.y - 6};
                                g.fillPolygon(xs, ys, 3);
                        }
                }

                // Seeds
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

                // Reference systems (light ring)
                g.setStroke(Color.LIGHTGREEN);
                for (Entity e : engine.entities()) {
                        if (!e.has(Reference.class) || !e.has(Transform.class)) continue;
                        Transform t = e.get(Transform.class);
                        g.strokeOval(t.x - 18, t.y - 18, 36, 36);
                }
        }

        private static void buildSampleLevel(GameEngine engine) {
                // System A (producer)
                Entity sysA = new Entity().add(new Transform(100, 200)).add(new Producer(0.8));
                engine.entities().add(sysA);

                Entity aOutSquare = new Entity()
                        .add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.SQUARE, sysA))
                        .add(new Transform(150, 200));
                engine.entities().add(aOutSquare);

                // System B (mid, IN + OUT)
                Entity sysB = new Entity().add(new Transform(350, 200));
                engine.entities().add(sysB);

                Entity bInSquare = new Entity()
                        .add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.SQUARE, sysB))
                        .add(new Transform(320, 200))
                        .add(new Queue(5));
                engine.entities().add(bInSquare);

                Entity bOutTri = new Entity()
                        .add(new PortInfo(PortInfo.IO.OUT, PortInfo.Shape.TRIANGLE, sysB))
                        .add(new Transform(380, 200));
                engine.entities().add(bOutTri);

                // System C (reference sink)
                Entity sysC = new Entity().add(new Transform(600, 200)).add(new Reference());
                engine.entities().add(sysC);

                Entity cInTri = new Entity()
                        .add(new PortInfo(PortInfo.IO.IN, PortInfo.Shape.TRIANGLE, sysC))
                        .add(new Transform(570, 200))
                        .add(new Queue(5));
                engine.entities().add(cInTri);

                // Links: A.outSquare -> B.inSquare ; B.outTri -> C.inTri
                engine.entities().add(new Entity().add(new Link(aOutSquare, bInSquare)));
                engine.entities().add(new Entity().add(new Link(bOutTri, cInTri)));
        }

        /** Small helper because your ShopView is app-specific. */
        private static final class ShopViewHelper {
                static void showShop(GameEngine engine, ShopSystem shop) {
                        try {
                                // Use your existing ShopView modal, but we need to pass engine/shop to controller.
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
