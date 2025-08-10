package play.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import play.level.LevelFactory;
import play.system.*;
import play.core.Entity;
import play.components.Transform;
import play.components.Seed;

import java.util.stream.Collectors;
import java.util.List;

public class GameMain extends Application {

        private final GameEngine engine = new GameEngine();
        private ShopSystem shopSystem;

        @Override
        public void start(Stage stage){
                Canvas canvas = new Canvas(900, 600);
                GraphicsContext g = canvas.getGraphicsContext2D();

                BorderPane root = new BorderPane();
                root.setCenter(new StackPane(canvas));
                HBox topHud = new HBox(12);
                topHud.setStyle("-fx-background-color: rgba(240,240,240,0.85); -fx-padding: 8px;");
                Button shopBtn = new Button("Shop (demo)");
                topHud.getChildren().addAll(shopBtn);
                root.setTop(topHud);

                stage.setScene(new Scene(root));
                stage.setTitle("Conduit Garden - Sandbox");
                stage.setResizable(false);
                stage.show();

                // demo level
                LevelFactory.buildDemo(engine);

                // systems with up-to-date signatures
                shopSystem = new ShopSystem(engine.entities());
                ProductionSystem prodSys = new ProductionSystem(engine, engine.entities(), 0.8);
                SeedMovementSystem moveSys = new SeedMovementSystem(engine, engine.entities(), shopSystem);
                CollisionSystem colSys = new CollisionSystem(engine, engine.entities(), shopSystem);

                // add a single orchestrator (Consumer<Double>)
                engine.addSystem(dt -> {
                        // apply shop toggles
                        shopSystem.update(dt);
                        // run gameplay systems
                        prodSys.update(dt);
                        moveSys.update(dt);
                        colSys.update(dt);
                        // render
                        render(g);
                });

                // demo shop click
                shopBtn.setOnAction(e -> {
                        int coins = 10;
                        double now = java.lang.System.currentTimeMillis()/1000.0;
                        shopSystem.purchaseAtar(coins, now);
                        shopSystem.purchaseAiryaman(coins, now);
                        shopSystem.purchaseAnahita(coins);
                });

                // simple animation/ticker
                new javafx.animation.AnimationTimer(){
                        private long last = 0;
                        @Override public void handle(long now){
                                if(last==0) { last=now; return; }
                                double dt = (now-last)/1e9;
                                last = now;
                                engine.tick(dt);
                        }
                }.start();

                // debug: space spawns a seed on the first link
                root.setOnKeyPressed(ev -> {
                        switch (ev.getCode()){
                                case SPACE -> {
                                        Entity seed = engine.createEntity();
                                        seed.add(new Transform(120,140));
                                        List<Entity> links = engine.entities().stream()
                                                .filter(x -> x.has(play.components.Link.class))
                                                .collect(Collectors.toList());
                                        if(!links.isEmpty()){
                                                Seed s = new Seed(Seed.Type.TRIANGLE);
                                                s.currentLink = links.get(0);
                                                seed.add(s);
                                        }
                                }
                        }
                });
                root.requestFocus();
        }

        private void render(GraphicsContext g){
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
                                g.setFill(Color.DARKGRAY);
                                g.fillRect(t.x-18, t.y-12, 36, 24);
                        }
                }

                g.setFill(Color.WHITE);
                g.setFont(Font.font(14));
                g.fillText("Entities: " + engine.entities().size(), 12, 18);
                g.fillText("Seeds: " + engine.entities().stream().filter(x->x.has(Seed.class)).count(), 12, 36);
        }

        public static void main(String[] args){ launch(args); }
}
