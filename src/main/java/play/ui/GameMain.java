package play.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import play.level.LevelFactory;
import play.system.*;
import play.core.Entity;
import play.components.Transform;
import play.components.Seed;
import play.system.System;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Main JavaFX app — builds engine, registers systems, provides a minimal HUD & shop UI.
 */
public class GameMain extends Application {

        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;

        @Override
        public void start(Stage stage){
                Canvas canvas = new Canvas(900, 600);
                GraphicsContext g = canvas.getGraphicsContext2D();

                // layout
                BorderPane root = new BorderPane();
                StackPane center = new StackPane(canvas);
                root.setCenter(center);
                HBox topHud = new HBox(12);
                topHud.setStyle("-fx-background-color: rgba(240,240,240,0.85); -fx-padding: 8px;");
                Button shopBtn = new Button("Shop");
                topHud.getChildren().addAll(shopBtn);
                root.setTop(topHud);

                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.setTitle("Conduit Garden - Blueprint Hell (ECS)");
                stage.setResizable(false);
                stage.show();

                // build demo level
                LevelFactory.buildDemo(engine);

                // create systems
                shopSystem = new ShopSystem(engine.entities());
                var prodSys = new ProductionSystem(engine.entities());
                var moveSys = new SeedMovementSystem(engine.entities());
                var colSys = new CollisionSystem(engine.entities());

                // wrap collision & movement to honor shop effects
                // We'll add a small adapter system to check shop flags each tick.
                engine.addSystem(new System() {
                        @Override public void update(double dt) {
                                // If collisions disabled -> skip collision processing
                                if(!shopSystem.getState().disableCollisions) colSys.update(dt);
                                // If lateral disabled -> zero lateral before movement
                                if(shopSystem.getState().disableLateral) {
                                        for(Entity e : engine.entities()){
                                                if(e.has(Seed.class)) e.get(Seed.class).lateral = 0;
                                        }
                                }
                                // movement & production always run
                                moveSys.update(dt);
                                prodSys.update(dt);
                                shopSystem.update(dt);
                        }
                });

                engine.setTickCallback(dt -> render(g));

                // animation loop
                new AnimationTimer(){
                        private long last = 0;
                        @Override public void handle(long now){
                                if(last==0) { last=now; return; }
                                double dt = (now-last)/1e9;
                                last = now;
                                engine.tick(dt);
                        }
                }.start();

                // shop interaction (simple toggles to demonstrate)
                shopBtn.setOnAction(e -> {
                        // demo purchase flow with local simple coin count
                        int coins = 10; // in real game: dynamic value from HUD/score service
                        double now = java.lang.System.currentTimeMillis()/1000.0;
                        // For demo we apply all three sequentially to show effects:
                        shopSystem.purchaseAtar(coins, now);
                        shopSystem.purchaseAiryaman(coins, now);
                        shopSystem.purchaseAnahita(coins);
                });

                // spawn seed with SPACE (debug)
                scene.setOnKeyPressed(ev -> {
                        if(ev.getCode() == KeyCode.SPACE){
                                Entity seed = engine.createEntity();
                                seed.add(new Transform(120,140));
                                Seed s = new Seed(Seed.Type.TRIANGLE);
                                // attach to first link found:
                                List<Entity> links = engine.entities().stream().filter(x -> x.has(play.components.Link.class)).collect(Collectors.toList());
                                if(!links.isEmpty()) s.currentLink = links.get(0);
                                seed.add(s);
                        }
                });
        }

        private void render(GraphicsContext g){
                // simple render: background + draw transforms + seeds + ports
                g.setFill(Color.web("#0d0d1a"));
                g.fillRect(0,0,900,600);

                for(Entity e : engine.entities()){
                        if(!e.has(Transform.class)) continue;
                        Transform t = e.get(Transform.class);
                        if(e.has(Seed.class)){
                                Seed s = e.get(Seed.class);
                                g.setFill(s.type==Seed.Type.SQUARE? Color.CORNFLOWERBLUE : Color.HOTPINK);
                                g.fillOval(t.x-6, t.y-6, 12, 12);
                        } else if(e.has(play.components.PortInfo.class)){
                                play.components.PortInfo p = e.get(play.components.PortInfo.class);
                                if(p.shape == play.components.PortInfo.Shape.SQUARE) {
                                        g.setFill(Color.LIGHTBLUE);
                                        g.fillRect(t.x-6, t.y-6, 12, 12);
                                } else {
                                        g.setFill(Color.LIGHTPINK);
                                        g.fillPolygon(new double[]{t.x,t.x+6,t.x-6}, new double[]{t.y+6,t.y-6,t.y-6}, 3);
                                }
                        } else {
                                // conduit boxes (just to mark conduits)
                                g.setFill(Color.DARKGRAY);
                                g.fillRect(t.x-18, t.y-12, 36, 24);
                        }
                }

                // HUD simple: in top-left show entity counts & small instructions
                g.setFill(Color.WHITE);
                g.setFont(Font.font(14));
                g.fillText("Entities: " + engine.entities().size(), 12, 18);
                g.fillText("Seeds: " + engine.entities().stream().filter(x->x.has(Seed.class)).count(), 12, 36);
                g.fillText("SPACE: spawn seed | Shop: demo apply items", 12, 54);
        }

        public static void main(String[] args){ launch(args); }
}
