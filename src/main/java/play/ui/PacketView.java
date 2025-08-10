package play.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import play.components.Seed;

/**
 * Packet renderer: draw only the core shape (square/triangle) as a filled shape.
 * No stroke/outline, no indicators, no transparency.
 */
public final class PacketView {
        private PacketView() {}

        public static void render(GraphicsContext g, Seed s, double cx, double cy) {
                final double size = UiConstants.PACKET_SIZE;
                final double half = size / 2.0;

                switch (s.type) {
                        case SQUARE -> {
                                // Filled square only
                                g.setFill(Color.web("#99d6ff")); // match port palette (square)
                                g.fillRect(cx - half, cy - half, size, size);
                        }
                        case TRIANGLE -> {
                                // Filled triangle only
                                double[] xs = { cx - half, cx + half, cx };
                                double[] ys = { cy + half, cy + half, cy - half };
                                g.setFill(Color.web("#ffc1d0")); // match port palette (triangle)
                                g.fillPolygon(xs, ys, 3);
                        }
                }
        }
}
