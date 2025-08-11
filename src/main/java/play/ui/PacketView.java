package play.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import play.components.Link;
import play.components.Seed;
import play.components.Transform;

/**
 * Packets are rendered as pure square/triangle.
 * Brightness shows RISK continuously:
 *   risk = max( |lateral| / lateralThreshold , collisions / capacity )
 * Flash from impactEnergy adds a quick pop but risk glow is always present.
 */
public final class PacketView {

        private PacketView() {}

        public static void render(GraphicsContext g, Seed s, double x, double y) {
                // ---- Position with lateral drift (perpendicular to link) ----
                double drawX = x, drawY = y;
                Link link = s.currentLink;
                if (link != null &&
                        link.fromPort != null && link.toPort != null &&
                        link.fromPort.has(Transform.class) && link.toPort.has(Transform.class)) {

                        Transform a = link.fromPort.get(Transform.class);
                        Transform b = link.toPort.get(Transform.class);
                        double dx = b.x - a.x, dy = b.y - a.y;
                        double len = Math.hypot(dx, dy);
                        if (len > 1e-4) {
                                double px = -dy / len, py =  dx / len;
                                drawX = x + px * s.lateral;
                                drawY = y + py * s.lateral;
                        }
                }

                // ---- Continuous risk: drift + collision budget ----
                double unitPx = UiConstants.PORT_SIZE;
                double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;
                double lateralThreshold = sizeUnits * unitPx * 1.25; // same as MovementSystem
                double driftRisk = clamp01(Math.abs(s.lateral) / Math.max(1e-6, lateralThreshold));
                double collRisk  = (s.capacity > 0) ? clamp01((double) s.collisions / (double) s.capacity) : 0.0;
                double risk = Math.max(driftRisk, collRisk); // conservative: show the worst

                // Impact pop (short-lived)
                double flash = clamp01(s.impactEnergy);

                // Combine: steady risk + flash pop; keep within [0..1]
                double intensity = clamp01(0.10 + 0.65 * risk + 0.40 * flash);

                // ---- Colors ----
                Color baseFill   = (s.type == Seed.Type.SQUARE) ? Color.web("#9ad9ff") : Color.web("#ffc1d0");
                Color baseStroke = (s.type == Seed.Type.SQUARE) ? Color.web("#1f6aa5") : Color.web("#a53d4e");

                // Lighten by intensity (brighter = closer to loss)
                double hue = baseFill.getHue();
                double sat = baseFill.getSaturation();
                double bri = baseFill.getBrightness();
                double newBri = clamp01(bri * (1.0 + 0.6 * intensity));
                double newSat = clamp01(sat * (1.0 - 0.30 * intensity));
                Color fill = Color.hsb(hue, newSat, newBri, 1.0);

                // Soft glow that grows with intensity
                double glowAlpha = 0.10 + 0.45 * intensity;
                double glowWidth = 1.0 + 5.0  * intensity;
                Color glowColor = (s.type == Seed.Type.SQUARE)
                        ? Color.rgb(223, 242, 255, glowAlpha)
                        : Color.rgb(255, 230, 238, glowAlpha);

                double ps = UiConstants.PACKET_SIZE;

                // ---- Draw ----
                g.setLineWidth(glowWidth);
                g.setStroke(glowColor);

                if (s.type == Seed.Type.SQUARE) {
                        double x0 = drawX - ps / 2.0, y0 = drawY - ps / 2.0;
                        g.strokeRect(x0, y0, ps, ps);           // glow
                        g.setFill(fill); g.fillRect(x0, y0, ps, ps);
                        g.setLineWidth(1.0); g.setStroke(baseStroke); g.strokeRect(x0, y0, ps, ps);
                } else {
                        double[] xs = {drawX - ps / 2.0, drawX + ps / 2.0, drawX};
                        double[] ys = {drawY + ps / 2.0, drawY + ps / 2.0, drawY - ps / 2.0};
                        g.strokePolygon(xs, ys, 3);             // glow
                        g.setFill(fill); g.fillPolygon(xs, ys, 3);
                        g.setLineWidth(1.0); g.setStroke(baseStroke); g.strokePolygon(xs, ys, 3);
                }
        }

        private static double clamp01(double v) { return (v < 0) ? 0 : (v > 1) ? 1 : v; }
}
