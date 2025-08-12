package play.render;

import javafx.scene.control.Label;
import play.model.core.Entity;
import play.model.engine.GameEngine;
import play.model.systems.System;

import java.util.List;
import java.util.function.DoubleSupplier;

/** View-side system to keep HUD labels in sync with the model. */
public class HudSystem implements System {

        private final GameEngine engine;
        private final List<Entity> entities;

        private final Label remainingWireLabel;
        private final Label entitiesLabel;
        private final Label seedsLabel;
        private final Label packetLossLabel;
        private final Label coinsLabel;
        private final Label timeLabel;

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
                if (remainingWireLabel != null) {
                        double left = Math.max(0, totalWire.getAsDouble() - usedWire.getAsDouble());
                        remainingWireLabel.setText(String.format("Wire Left: %.0f", left));
                }

                if (entitiesLabel != null) {
                        entitiesLabel.setText("Entities: " + entities.size());
                }

                if (seedsLabel != null) {
                        int seedCount = 0;
                        for (Entity e : entities) if (e.has(play.model.components.Seed.class)) seedCount++;
                        seedsLabel.setText("Seeds: " + seedCount);
                }

                if (packetLossLabel != null) {
                        int lost = engine.getLostCount();
                        int planned = engine.getPlannedTotal();
                        double lossPct = (planned == 0) ? 0 : (100.0 * lost / planned);
                        packetLossLabel.setText(String.format("Loss: %d/%d (%.0f%%)", lost, planned, lossPct));
                }

                if (coinsLabel != null) {
                        coinsLabel.setText("Coins: " + engine.getCoins());
                }

                if (timeLabel != null) {
                        int secs = (int) Math.ceil(timeRemaining.getAsDouble());
                        int m = Math.max(0, secs / 60), s = Math.max(0, secs % 60);
                        timeLabel.setText(String.format("Time: %02d:%02d", m, s));
                }
        }
}
