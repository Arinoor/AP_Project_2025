
package play.view;

import javafx.geometry.Pos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import play.model.components.BitPacket;
import play.model.components.HeavyPacket;
import play.model.components.Seed;
import play.model.core.Entity;

/**
 * Draws packets (seeds).
 * - SQUARE/TRIANGLE are vector shapes.
 * - INFINITE/SECURE/PROTECTED are sprites.
 *
 * Visual size scales with Seed.sizeUnits():
 *   SQUARE=2 -> 1.0x, TRIANGLE=3 -> 1.5x, INFINITE=1 -> 0.5x, SECURE=4 -> 2.0x,
 *   PROTECTED -> 2x of its primary's (hidden) sizeUnits.
 */
public final class PacketView {
        private PacketView() {}

        /** Base visual size (in px) that will be scaled by sizeUnits() / 2.0. */
        private static final double BASE_SIZE = UiConstants.PACKET_SIZE;

        // Lazy-loaded, cached sprites
        private static Image INFINITE_IMG;
        private static Image SECURE_IMG;
        private static Image PROTECTED_IMG;
        private static Image SECURE_PROTECTED_IMG;
        private static Image HEAVY_IMG;


        /** Render a packet centered at (x,y). */
        public static void render(GraphicsContext g, Seed s, double x, double y) {
                final double sizeFactor = s.sizeUnits() / 2.0;
                final double VISUAL_SIZE = BASE_SIZE * sizeFactor;

                if (s.type == Seed.Type.INFINITE) {
                        renderSprite(g, s, x, y, VISUAL_SIZE, getInfiniteImage(), 1.35, 0.62, 0.10, 0.70, 0.20);
                        return;
                }
                if (s.type == Seed.Type.SECURE) {
                        renderSprite(g, s, x, y, VISUAL_SIZE, getSecureImage(),   1.25, 0.58, 0.08, 0.60, 0.15);
                        return;
                }
                if (s.type == Seed.Type.PROTECTED) {
                        renderSprite(g, s, x, y, VISUAL_SIZE, getProtectedImage(),1.30, 0.60, 0.10, 0.65, 0.18);
                        return;
                }
                if (s.type == Seed.Type.SECURE_PROTECTED) {
                        renderSprite(g, s, x, y, VISUAL_SIZE, getSecureProtectedImage(), 1.35, 0.62, 0.10, 0.70, 0.20);
                        return;
                }

                if (s.type == Seed.Type.HEAVY) {
                        renderSprite(g, s, x, y, VISUAL_SIZE, getHeavyImage(), 1.25, 0.58, 0.08, 0.60, 0.15);
                        return;
                }
                if (s.type == Seed.Type.BITPACKET) {
                        // render as a colored circle
                        double r = VISUAL_SIZE * 0.5;
                        Color col = Color.web("#cccccc");
                        if (s.colorRgb != 0) {
                                int rgb = s.colorRgb & 0xFFFFFF;
                                int rr = (rgb >> 16) & 0xFF;
                                int gg = (rgb >> 8) & 0xFF;
                                int bb = rgb & 0xFF;
                                col = Color.rgb(rr, gg, bb);
                        }
                        g.setFill(col);
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                        // inner highlight
                        g.setFill(Color.rgb(255,255,255,0.18));
                        g.fillOval(x - r*0.5, y - r*0.5, r, r);
                        return;
                }


                // ==== Vector shapes (Square / Triangle) ====
                final Color baseFill = (s.type == Seed.Type.SQUARE)
                        ? Color.web("#9ad9ff")
                        : Color.web("#ffc1d0");

                final double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                final double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));

                final double noiseNorm = clamp01(s.noise / s.sizeUnits());
                final Color noiseColor = Color.web("#ffd166", 0.15 + 0.75 * noiseNorm);

                final double impactFlash = clamp01(s.impactEnergy);
                final double glowAlpha = clamp01(0.10 + 0.70 * risk + 0.20 * impactFlash);

                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        drawShape(g, s, x, y, VISUAL_SIZE * 1.35, Color.WHITE);
                        g.setGlobalAlpha(1.0);
                }

                drawShape(g, s, x, y, VISUAL_SIZE, baseFill);

                if (noiseNorm > 0.01) {
                        drawShape(g, s, x, y, VISUAL_SIZE * 0.62, noiseColor);
                }

                if (s.trojan) {
                        double badgeSize = Math.max(4.0, VISUAL_SIZE * 0.28);
                        double ox = x + VISUAL_SIZE * 0.45 - badgeSize * 0.5;
                        double oy = y - VISUAL_SIZE * 0.45 - badgeSize * 0.5;
                        g.setFill(Color.RED);
                        g.fillOval(ox, oy, badgeSize, badgeSize);
                        // optional: small inner highlight
                        g.setFill(Color.rgb(255,180,180));
                        g.fillOval(ox + badgeSize*0.22, oy + badgeSize*0.22, badgeSize*0.56, badgeSize*0.56);
                }
        }

        // ---- Sprite helpers ----

        private static void renderSprite(GraphicsContext g, Seed s, double x, double y,
                                         double visualSize, Image sprite,
                                         double haloScale, double innerScale,
                                         double glowBase, double glowRisk, double glowImpact) {
                final double lateralThresholdPx = s.sizeUnits() * UiConstants.PORT_SIZE;
                final double risk = clamp01(Math.abs(s.lateral) / Math.max(1.0, lateralThresholdPx));
                final double impactFlash = clamp01(s.impactEnergy);
                final double glowAlpha = clamp01(glowBase + glowRisk * risk + glowImpact * impactFlash);

                if (glowAlpha > 0.01) {
                        g.setGlobalAlpha(glowAlpha);
                        final double r = visualSize * haloScale * 0.5;
                        g.setFill(Color.WHITE);
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                        g.setGlobalAlpha(1.0);
                }

                final double half = visualSize * 0.5;
                g.drawImage(sprite, x - half, y - half, visualSize, visualSize);

                final double noiseNorm = clamp01(s.noise / s.sizeUnits());
                if (noiseNorm > 0.01) {
                        g.setFill(Color.web("#ffd166", 0.12 + 0.60 * noiseNorm));
                        final double r = visualSize * innerScale * 0.5;
                        g.fillOval(x - r, y - r, r * 2, r * 2);
                }
        }

        private static Image getInfiniteImage() {
                if (INFINITE_IMG == null) {
                        INFINITE_IMG = new Image(PacketView.class.getResourceAsStream("/img/infinite_packet.png"));
                }
                return INFINITE_IMG;
        }

        private static Image getSecureImage() {
                if (SECURE_IMG == null) {
                        SECURE_IMG = new Image(PacketView.class.getResourceAsStream("/img/secure_packet.png"));
                }
                return SECURE_IMG;
        }

        private static Image getProtectedImage() {
                if (PROTECTED_IMG == null) {
                        PROTECTED_IMG = new Image(PacketView.class.getResourceAsStream("/img/protected_packet.png"));
                }
                return PROTECTED_IMG;
        }

        private static Image getSecureProtectedImage() {
                if (SECURE_PROTECTED_IMG == null) {
                        SECURE_PROTECTED_IMG = new Image(PacketView.class.getResourceAsStream("/img/secure_protected_packet.png"));
                }
                return SECURE_PROTECTED_IMG;
        }

        private static Image getHeavyImage() {
                if (HEAVY_IMG == null) {
                        HEAVY_IMG = new Image(PacketView.class.getResourceAsStream("/img/heavy_packet.png"));
                }
                return HEAVY_IMG;
        }

        // ---- Vector primitives ----

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

