package play.ui;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import play.level.LevelLoader;
import play.system.*;

import java.io.IOException;
import java.util.List;

import play.core.Entity;
import play.components.Seed;
import play.components.Transform;

public class MainController {

        @FXML private Canvas gameCanvas;
        @FXML private AnchorPane canvasContainer;
        @FXML private Label entitiesLabel;
        @FXML private Label seedsLabel;
        @FXML private Label coinsLabel;
        @FXML private Button shopButton;
        @FXML private Button startButton;

        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;
        private AnimationTimer timer;
        private double coins = 0;

        @FXML
        private void initialize() {
                // load level (JSON resource) -> constructs entities in engine
                try {
                        LevelLoader.loadFromResource("/levels/demo-level.json", engine);
                } catch (Exception e) {
                        e.printStackTrace();
                        // fallback to in-code factory if JSON fails
                        play.level.LevelFactory.buildDemo(engine);
                }

                // create systems
                shopSystem = new ShopSystem(engine.entities());
                var prod = new ProductionSystem(engine.entities());
                var move = new SeedMovementSystem(engine.entities(), shopSystem);
                var collision = new CollisionSystem(engine.entities(), shopSystem);

                // register a single system wrapper that orchestrates order
                engine.addSystem(dt -> {
                        // update shop => manages timers
                        shopSystem.update(dt);

                        // production then movement then collisions (order matters)
                        prod.update(dt);
                        move.update(dt);
                        collision.update(dt);
                });

                engine.setTickCallback(dt -> render());

                // animation
                timer = new AnimationTimer() {
                        private long last = 0;
                        @Override public void handle(long now) {
                                if (last == 0) { last = now; return; }
                                double dt = (now - last) / 1e9;
                                last = now;
                                engine.tick(dt);
                                updateHud();
                        }
                };

                // button handlers
                shopButton.setOnAction(e -> openShop());
                startButton.setOnAction(e -> {
                        if (timer != null) timer.start();
                        startButton.setDisable(true);
                });

                updateHud();
        }

        private void render() {
                GraphicsContext g = gameCanvas.getGraphicsContext2D();
                g.setFill(javafx.scene.paint.Color.web("#0d0d1a"));
                g.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
                for (Entity en : engine.entities()) {
                        if (!en.has(Transform.class)) continue;
                        Transform t = en.get(Transform.class);
                        if (en.has(Seed.class)) {
                                Seed s = en.get(Seed.class);
                                g.setFill(s.type == Seed.Type.SQUARE ? javafx.scene.paint.Color.CORNFLOWERBLUE : javafx.scene.paint.Color.HOTPINK);
                                g.fillOval(t.x - 6, t.y - 6, 12, 12);
                        } else if (en.has(play.components.PortInfo.class)) {
                                play.components.PortInfo p = en.get(play.components.PortInfo.class);
                                if (p.shape == play.components.PortInfo.Shape.SQUARE) {
                                        g.setFill(javafx.scene.paint.Color.LIGHTBLUE);
                                        g.fillRect(t.x - 6, t.y - 6, 12, 12);
                                } else {
                                        g.setFill(javafx.scene.paint.Color.LIGHTPINK);
                                        g.fillPolygon(new double[]{t.x, t.x + 6, t.x - 6}, new double[]{t.y + 6, t.y - 6, t.y - 6}, 3);
                                }
                        } else {
                                g.setFill(javafx.scene.paint.Color.DARKGRAY);
                                g.fillRect(t.x - 18, t.y - 12, 36, 24);
                        }
                }
        }

        private void updateHud() {
                List<Entity> ents = engine.entities();
                entitiesLabel.setText("Entities: " + ents.size());
                long seedCount = ents.stream().filter(e -> e.has(Seed.class)).count();
                seedsLabel.setText("Seeds: " + seedCount);
                coinsLabel.setText("Coins: " + (int) coins);
        }

        private void openShop() {
                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/shop.fxml"));
                        Stage shopStage = new Stage();
                        shopStage.initOwner(canvasContainer.getScene().getWindow());
                        shopStage.initModality(Modality.APPLICATION_MODAL);
                        shopStage.setScene(new javafx.scene.Scene(loader.load()));
                        ShopController sc = loader.getController();
                        sc.setDependencies(shopSystem, this);
                        shopStage.showAndWait();
                        updateHud();
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
        }

        // API used by ShopController to charge coins or grant coins
        public boolean chargeCoins(int cost) {
                if (coins >= cost) { coins -= cost; return true; }
                return false;
        }
        public void addCoins(int amount) { coins += amount; }
}
