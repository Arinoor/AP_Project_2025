package play.model.services;

import play.model.engine.GameEngine;
import play.model.level.LevelLoaderV2;

public class GameService {
        private final GameEngine engine = new GameEngine();
        private LevelLoaderV2.Loaded loaded;
        private String currentLevel;

        public GameEngine engine() { return engine; } // controllers may pass to RenderSystem

        public void loadLevel(String path) {
                engine.resetForLevel();
                loaded = LevelLoaderV2.loadFromResource(engine, path);
                currentLevel = path;
                // remove any links injected by loaders
                engine.entities().removeIf(e -> e.has(play.model.components.Link.class));
        }

        public String currentLevel() { return currentLevel; }
        public double totalWire() { return (loaded != null ? loaded.totalWire : 3000); }
        public int timeLimitSeconds() { return (loaded != null ? loaded.timeLimitSeconds : 120); }
        public int plannedSeeds() { return (loaded != null ? loaded.plannedSeeds : 0); }

        public void tick(double dt) { engine.tick(dt); }
}
