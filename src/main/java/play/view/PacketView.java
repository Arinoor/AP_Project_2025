package play.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import play.model.components.Seed;

/**
 * Draws packets (seeds).
 * - SQUARE/TRIANGLE are vector shapes.
 * - INFINITE/SECURE are sprites.
 *
 * Visual size now scales with Seed.sizeUnits():
 *   SQUARE=2 -> 1.0x, TRIANGLE=3 -> 1.5x, INFINITE=1 -> 0.5x, SECURE=4 -> 2.0x
 */
public final class PacketView {
        private PacketView() {}

        /** Base visual size (in px) that will be scaled by sizeUnits() / 2.0. */
        private static final double BASE_SIZE = UiConstants.PACKET_SIZE;

        // Lazy-loaded, cached sprites
        private static Image INFINITE_IMG;
        private static Image SECURE_IMG;

        /**
         * Render a packet centered at (x,y).
         */
        public static void render(GraphicsContext g, Seed s, double x, double y) {
                // Compute visual size once based on sizeUnits (see class header)
                final double sizeFactor = s.sizeUnits() / 2.0;     // 1.0x for 2 units, etc.
                final double VISUAL_SIZE = BASE_SIZE * sizeFactor;

                if (s.type == Seed.Type.INFINITE) {
                        renderInfinite(g, s, x, y, VISUAL_SIZE);
                        return;
                }
                if (s.type == Seed.Type.SECURE) {
                        renderSecure(g, s, x, y, VISUAL_SIZE);
                        return;
                }

                // ==== Colors ====
                final Color baseFill = (s.type == Seed.Type.SQUARE)
                        ? Color.web("#9ad9ff")
                        : Color.web("#ffc1d0");

                // Lateral risk glow => white bloom (outer), keep threshold coupled to sizeUnits (port geometry)
                final double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                final double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));

                // Noise inner core => amber fill intensity grows with noise fraction
                final double noiseNorm = clamp01(s.noise / s.sizeUnits());
                final Color noiseColor = Color.web("#ffd166", 0.15 + 0.75 * noiseNorm);

                // Optional flash from recent impact
                final double impactFlash = clamp01(s.impactEnergy);
                final double glowAlpha = clamp01(0.10 + 0.70 * risk + 0.20 * impactFlash);

                // Bloom (slightly larger, soft alpha)
                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        drawShape(g, s, x, y, VISUAL_SIZE * 1.35, Color.WHITE);
                        g.setGlobalAlpha(1.0);
                }

                // Base body
                drawShape(g, s, x, y, VISUAL_SIZE, baseFill);

                // Inner noise core (smaller, amber)
                if (noiseNorm > 0.01) {
                        drawShape(g, s, x, y, VISUAL_SIZE * 0.62, noiseColor);
                }
        }

        private static void renderInfinite(GraphicsContext g, Seed s, double x, double y, double visualSize) {
                if (INFINITE_IMG == null) {
                        INFINITE_IMG = new Image(PacketView.class.getResourceAsStream("/img/infinite_packet.png"));
                }

                // Glow halo based on lateral risk and recent impact
                final double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                final double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));
                final double impactFlash = clamp01(s.impactEnergy);
                final double glowAlpha = clamp01(0.10 + 0.70 * risk + 0.20 * impactFlash);

                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        final double r = visualSize * 1.35 * 0.5;
                        g.setFill(Color.WHITE);
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                        g.setGlobalAlpha(1.0);
                }

                // Base sprite
                final double half = visualSize * 0.5;
                g.drawImage(INFINITE_IMG, x - half, y - half, visualSize, visualSize);

                // Subtle inner noise overlay (amber disk) to preserve feedback
                final double noiseNorm = clamp01(s.noise / s.sizeUnits());
                if (noiseNorm > 0.01) {
                        g.setFill(Color.web("#ffd166", 0.15 + 0.75 * noiseNorm));
                        final double r = visualSize * 0.62 * 0.5;
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                }
        }

        private static void renderSecure(GraphicsContext g, Seed s, double x, double y, double visualSize) {
                if (SECURE_IMG == null) {
                        SECURE_IMG = new Image(PacketView.class.getResourceAsStream("/img/secure_packet.png"));
                }

                // Gentle bloom (similar treatment to infinite)
                final double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                final double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));
                final double impactFlash = clamp01(s.impactEnergy);
                final double glowAlpha = clamp01(0.08 + 0.60 * risk + 0.15 * impactFlash);

                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        final double r = visualSize * 1.25 * 0.5;
                        g.setFill(Color.WHITE);
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                        g.setGlobalAlpha(1.0);
                }

                // Base sprite
                final double half = visualSize * 0.5;
                g.drawImage(SECURE_IMG, x - half, y - half, visualSize, visualSize);

                // Inner noise overlay (subtle)
                final double noiseNorm = clamp01(s.noise / s.sizeUnits());
                if (noiseNorm > 0.01) {
                        g.setFill(Color.web("#ffd166", 0.12 + 0.60 * noiseNorm));
                        final double r = visualSize * 0.58 * 0.5;
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                }
        }

        /**
         * Draw vector shapes for SQUARE / TRIANGLE at the given size.
         */
        private static void drawShape(GraphicsContext g, Seed s, double x, double y, double size, Color fill) {
                g.setFill(fill);
                if (s.type == Seed.Type.SQUARE) {
                        final double half = size * 0.5;
                        g.fillRect(x - half, y - half, size, size);
                } else { // TRIANGLE
                        final double half = size * 0.5;
                        final double[] xs = { x - half, x + half, x };
                        final double[] ys = { y + half, y + half, y - half };
                        g.fillPolygon(xs, ys, 3);
                }
        }

        private static double clamp01(double v) {
                return (v < 0) ? 0 : (v > 1) ? 1 : v;
        }
}
