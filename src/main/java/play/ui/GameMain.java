package play.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import play.system.GameEngine;
import play.system.SeedMovementSystem;
import play.system.CollisionSystem;
import play.core.Entity;
import play.components.Transform;
import play.components.Seed;
import play.level.LevelFactory;

import java.util.ArrayList;

public class GameMain extends Application {
        private final GameEngine engine = new GameEngine();
        private final ArrayList<Entity> sharedList = new ArrayList<>();
        private long last = 0;

        @Override
        public void start(Stage stage){
                Canvas canvas = new Canvas(800, 480);
                GraphicsContext g = canvas.getGraphicsContext2D();
                StackPane root = new StackPane(canvas);
                Scene s = new Scene(root);
                stage.setScene(s);
                stage.setTitle("Conduit Garden (baseline)");
                stage.show();

                // create demo level and systems
                LevelFactory.buildDemo(engine);
                // give systems a view of engine.entities (simple)
                engine.addSystem(new SeedMovementSystem(engine.entities()));
                engine.addSystem(new CollisionSystem(engine.entities()));

                engine.setTickCallback(dt -> render(g));

                AnimationTimer timer = new AnimationTimer(){
                        @Override
                        public void handle(long now){
                                if(last==0) { last = now; return; }
                                double dt = (now-last)/1e9;
                                last = now;
                                engine.tick(dt);
                        }
                };
                timer.start();

                s.setOnKeyPressed(ev -> {
                        if(ev.getCode()== KeyCode.SPACE) {
                                // spawn a seed at runtime for testing
                                Entity seed = engine.createEntity();
                                seed.add(new Transform(120,140));
                                Seed sd = new Seed(Seed.Type.TRIANGLE);
                                seed.add(sd);
                                // attach to first link
                                if(!engine.entities().isEmpty()){
                                        for(Entity e: engine.entities()){
                                                if(e.has(play.components.Link.class)){
                                                        sd.currentLink = e; break;
                                                }
                                        }
                                }
                        }
                });
        }

        private void render(GraphicsContext g){
                g.setFill(Color.web("#111"));
                g.fillRect(0,0,800,480);
                // draw entities with Transform+Seed
                for(Entity e : engine.entities()){
                        if(e.has(Transform.class)){
                                Transform t = e.get(Transform.class);
                                if(e.has(Seed.class)){
                                        Seed sd = e.get(Seed.class);
                                        g.setFill(sd.type==Seed.Type.SQUARE? Color.CORNFLOWERBLUE : Color.PINK);
                                        g.fillOval(t.x-6, t.y-6, 12, 12);
                                } else if(e.has(play.components.PortInfo.class)){
                                        var p = e.get(play.components.PortInfo.class);
                                        g.setFill(p.shape==play.components.PortInfo.Shape.SQUARE? Color.LIGHTBLUE : Color.PINK);
                                        g.fillRect(t.x-5, t.y-5, 10, 10);
                                } else {
                                        // generic conduit box marker
                                        g.setFill(Color.DARKGRAY);
                                        g.fillRect(t.x-16, t.y-12, 32, 24);
                                }
                        }
                }
        }

        public static void main(String[] args){ launch(); }
}
