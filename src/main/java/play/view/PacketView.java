package play.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import play.model.components.Seed;

/** Draws packets (seeds). INFINITE uses an image; others are vector shapes. */
public final class PacketView {
        private PacketView() {}

        /** Base visual size for packets (in px). */
        private static final double SIZE = UiConstants.PACKET_SIZE;

        // Lazy-loaded, cached sprite for INFINITE
        private static Image INFINITE_IMG;

        public static void render(GraphicsContext g, Seed s, double x, double y) {
                if (s.type == Seed.Type.INFINITE) {
                        renderInfinite(g, s, x, y);
                        return;
                }

                // ==== Colors ====
                final Color baseFill = (s.type == Seed.Type.SQUARE)
                        ? Color.web("#9ad9ff")
                        : Color.web("#ffc1d0");

                // Lateral risk glow => white bloom (outer)
                double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));

                // Noise inner core => amber fill intensity grows with noise fraction
                double noiseNorm = clamp01(s.noise / s.sizeUnits());
                final Color noiseColor = Color.web("#ffd166", 0.15 + 0.75 * noiseNorm);

                // Optional flash from recent impact
                double impactFlash = clamp01(s.impactEnergy);
                double glowAlpha = clamp01(0.10 + 0.70 * risk + 0.20 * impactFlash);

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

        private static void renderInfinite(GraphicsContext g, Seed s, double x, double y) {
                if (INFINITE_IMG == null) {
                        // load once; expect resource under resources/img/infinite_packet.png
                        INFINITE_IMG = new Image(PacketView.class.getResourceAsStream("/img/infinite_packet.png"));
                }

                // bloom halo
                double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));
                double impactFlash = clamp01(s.impactEnergy);
                double glowAlpha = clamp01(0.10 + 0.70 * risk + 0.20 * impactFlash);
                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        double r = SIZE * 1.35 * 0.5;
                        g.setFill(Color.WHITE);
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                        g.setGlobalAlpha(1.0);
                }

                // base sprite
                double half = SIZE * 0.5;
                g.drawImage(INFINITE_IMG, x - half, y - half, SIZE, SIZE);

                // subtle inner noise overlay (amber disk) to preserve feedback
                double noiseNorm = clamp01(s.noise / s.sizeUnits());
                if (noiseNorm > 0.01) {
                        g.setFill(Color.web("#ffd166", 0.15 + 0.75 * noiseNorm));
                        double r = SIZE * 0.62 * 0.5;
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                }
        }

        private static void drawShape(GraphicsContext g, Seed s, double x, double y, double size, Color fill) {
                g.setFill(fill);
                if (s.type == Seed.Type.SQUARE) {
                        double half = size * 0.5;
                        g.fillRect(x - half, y - half, size, size);
                } else { // triangle
                        double half = size * 0.5;
                        double[] xs = { x - half, x + half, x };
                        double[] ys = { y + half, y + half, y - half };
                        g.fillPolygon(xs, ys, 3);
                }
        }

        private static double clamp01(double v) { return (v < 0) ? 0 : (v > 1) ? 1 : v; }
}
