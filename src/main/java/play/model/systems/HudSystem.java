package play.model.systems;

import play.model.core.Entity;
import play.model.components.Seed;

import javafx.scene.control.Label;
import play.model.engine.GameEngine;

import java.util.List;
import java.util.function.DoubleSupplier;

public class HudSystem implements System {

        private final List<Entity> entities;
        private final GameEngine engine;

        // UI refs
        private final Label remainingWireLabel;
        private final Label entitiesLabel;
        private final Label seedsLabel;
        private final Label packetLossLabel;
        private final Label coinsLabel;
        private final Label timeLabel;

        // Data suppliers (owned by MainController; HudSystem reads them)
        private final DoubleSupplier totalWire;
        private final DoubleSupplier usedWire;
        private final DoubleSupplier timeRemaining;

        public HudSystem(
                GameEngine engine,
                List<Entity> entities,
                Label remainingWireLabel,
                Label entitiesLabel,
                Label seedsLabel,
                Label packetLossLabel,
                Label coinsLabel,
                Label timeLabel,
                DoubleSupplier totalWire,
                DoubleSupplier usedWire,
                DoubleSupplier timeRemaining
        ) {
                this.engine = engine;
                this.entities = entities;
                this.remainingWireLabel = remainingWireLabel;
                this.entitiesLabel = entitiesLabel;
                this.seedsLabel = seedsLabel;
                this.packetLossLabel = packetLossLabel;
                this.coinsLabel = coinsLabel;
                this.timeLabel = timeLabel;
                this.totalWire = totalWire;
                this.usedWire = usedWire;
                this.timeRemaining = timeRemaining;
        }

        @Override
        public void update(double dt) {
                // Remaining wire
                double rem = Math.max(0, totalWire.getAsDouble() - usedWire.getAsDouble());
                remainingWireLabel.setText(String.format("Wire Left: %.0f", rem));

                // Entities count
                entitiesLabel.setText("Entities: " + entities.size());

                // Seed count
                int seedCount = 0;
                for (Entity e : entities) if (e.has(Seed.class)) seedCount++;
                seedsLabel.setText("Seeds: " + seedCount);

                // Packet loss
                int loss = engine.getLostCount();
                int planned = engine.getPlannedTotal();
                double lossPct = (planned == 0) ? 0.0 : (100.0 * loss / planned);
                packetLossLabel.setText(String.format("Loss: %d/%d (%.0f%%)", loss, planned, lossPct));

                // Coins
                coinsLabel.setText("Coins: " + engine.getCoins());

                // Time mm:ss
                int secs = (int) Math.ceil(Math.max(0.0, timeRemaining.getAsDouble()));
                int m = Math.max(0, secs / 60), s = Math.max(0, secs % 60);
                timeLabel.setText(String.format("Time: %02d:%02d", m, s));
        }
}
