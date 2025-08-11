package play.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import play.components.Seed;

public final class PacketView {

        private PacketView() {}

        /** Base visual size for packets (in px). */
        private static final double SIZE = UiConstants.PACKET_SIZE; // keep your constant

        public static void render(GraphicsContext g, Seed s, double x, double y) {
                // ==== Colors ====
                // Base fill by type (match your port palette)
                final Color baseFill = (s.type == Seed.Type.SQUARE)
                        ? Color.web("#9ad9ff")    // square
                        : Color.web("#ffc1d0");   // triangle

                // Lateral risk glow => white bloom (outer)
                // Risk normalized by lateral threshold (computed elsewhere); we approximate via sizeUnits
                double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));

                // Noise inner core => amber fill intensity grows with noise fraction
                double noiseNorm = clamp01(s.noise / s.sizeUnits());
                final Color noiseColor = Color.web("#ffd166", 0.15 + 0.75 * noiseNorm); // amber, brighter with noise

                // Optional flash from recent impact
                double impactFlash = clamp01(s.impactEnergy);
                double glowAlpha = clamp01(0.10 + 0.70 * risk + 0.20 * impactFlash);

                // ==== Draw order: bloom -> base -> noise core ====

                // Bloom (slightly larger, soft alpha)
                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        drawShape(g, s, x, y, SIZE * 1.35, Color.WHITE);
                        g.setGlobalAlpha(1.0);
                }

                // Base body
                drawShape(g, s, x, y, SIZE, baseFill);

                // Inner noise core (smaller, amber)
                if (noiseNorm > 0.01) {
                        drawShape(g, s, x, y, SIZE * 0.62, noiseColor);
                }
        }

        private static void drawShape(GraphicsContext g, Seed s, double x, double y, double size, Color fill) {
                g.setFill(fill);
                if (s.type == Seed.Type.SQUARE) {
                        double half = size * 0.5;
                        g.fillRect(x - half, y - half, size, size);
                } else {
                        double half = size * 0.5;
                        double[] xs = { x - half, x + half, x };
                        double[] ys = { y + half, y + half, y - half };
                        g.fillPolygon(xs, ys, 3);
                }
        }

        private static double clamp01(double v) {
                return (v < 0) ? 0 : (v > 1) ? 1 : v;
        }
}
