package play.model.services;

import play.model.engine.GameEngine;
import play.model.level.LevelDto;
import play.model.level.LevelFactory;
import play.model.level.LevelParser;

public final class GameService {
        private final GameEngine engine = new GameEngine();

        private double totalWire = 3000;
        private int timeLimitSeconds = 120;
        private int plannedSeeds = 0;
        private String currentPath = null;

        public void loadLevel(String resourcePath) {
                currentPath = resourcePath;
                LevelDto dto = LevelParser.parseFromResource(resourcePath);
                LevelFactory.Result res = LevelFactory.applyToEngine(engine, dto);
                this.totalWire = res.totalWire();
                this.timeLimitSeconds = res.timeLimitSeconds();
                this.plannedSeeds = res.plannedSeeds();
                engine.setPlannedTotal(plannedSeeds);
        }

        public GameEngine engine() { return engine; }
        public double totalWire() { return totalWire; }
        public int timeLimitSeconds() { return timeLimitSeconds; }
        public int plannedSeeds() { return plannedSeeds; }
        public String currentLevelPath() { return currentPath; }
}
