package play.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import play.components.Link;
import play.components.Seed;
import play.components.Transform;

/**
 * Renders packets as simple square/triangle.
 * Visuals:
 *  - Position is offset perpendicular to the current link by Seed.lateral (drift).
 *  - Brightness/glow scales with risk: |lateral| / lateralThreshold (threshold depends on type size).
 *  - Impact flash adds a short-lived boost via Seed.impactEnergy.
 *
 * Thresholds:
 *  - Square   size = 2 units
 *  - Triangle size = 3 units
 *  - pixels-per-unit is UiConstants.PORT_SIZE
 */
public final class PacketView {

        private PacketView() {}

        public static void render(GraphicsContext g, Seed s, double x, double y) {
                // ---- Position with lateral drift (perpendicular to link) ----
                double drawX = x;
                double drawY = y;
                Link link = s.currentLink;
                if (link != null &&
                        link.fromPort != null && link.toPort != null &&
                        link.fromPort.has(Transform.class) && link.toPort.has(Transform.class)) {

                        Transform a = link.fromPort.get(Transform.class);
                        Transform b = link.toPort.get(Transform.class);
                        double dx = b.x - a.x, dy = b.y - a.y;
                        double len = Math.hypot(dx, dy);
                        if (len > 1e-4) {
                                double px = -dy / len;
                                double py =  dx / len;
                                drawX = x + px * s.lateral;
                                drawY = y + py * s.lateral;
                        }
                }

                // ---- Risk-based brightness ----
                double unitPx = UiConstants.PORT_SIZE;                         // pixels per "unit"
                double sizeUnits = (s.type == Seed.Type.SQUARE) ? 2.0 : 3.0;  // doc sizes
                double lossThreshold = sizeUnits * unitPx;                     // same as SeedMovementSystem
                double risk = clamp01(Math.abs(s.lateral) / Math.max(1e-6, lossThreshold)); // 0..1

                // Short-lived flash from impact energy (already decays in movement system)
                double flash = clamp01(s.impactEnergy);

                // Combine: risk drives steady glow; flash gives a quick pop
                // Keep within 0..1 for interpolation factors
                double intensity = clamp01(0.15 + 0.75 * risk + 0.35 * flash);

                // ---- Colors ----
                Color baseFill   = (s.type == Seed.Type.SQUARE) ? Color.web("#9ad9ff") : Color.web("#ffc1d0");
                Color baseStroke = (s.type == Seed.Type.SQUARE) ? Color.web("#1f6aa5") : Color.web("#a53d4e");

                // Lighten by increasing brightness and slightly reducing saturation as intensity rises
                double hue = baseFill.getHue();
                double sat = baseFill.getSaturation();
                double bri = baseFill.getBrightness();
                double newBri = clamp01(bri * (1.0 + 0.6 * intensity));
                double newSat = clamp01(sat * (1.0 - 0.30 * intensity));
                Color fill = Color.hsb(hue, newSat, newBri, 1.0);

                // A soft glow outline; brighter/ wider as risk grows
                double glowAlpha = 0.08 + 0.50 * intensity;    // 0.08 .. 0.58
                double glowWidth = 1.0 + 5.0  * intensity;     // 1 .. 6 px
                Color glowColor = (s.type == Seed.Type.SQUARE)
                        ? Color.rgb(223, 242, 255, glowAlpha)  // light-cyan glow
                        : Color.rgb(255, 230, 238, glowAlpha); // light-pink glow

                double ps = UiConstants.PACKET_SIZE;

                // ---- Draw ----
                g.setLineWidth(glowWidth);
                g.setStroke(glowColor);

                if (s.type == Seed.Type.SQUARE) {
                        double x0 = drawX - ps / 2.0, y0 = drawY - ps / 2.0;
                        // glow stroke
                        g.strokeRect(x0, y0, ps, ps);
                        // fill
                        g.setFill(fill);
                        g.fillRect(x0, y0, ps, ps);
                        // thin outline
                        g.setLineWidth(1.0);
                        g.setStroke(baseStroke);
                        g.strokeRect(x0, y0, ps, ps);
                } else {
                        double[] xs = {drawX - ps / 2.0, drawX + ps / 2.0, drawX};
                        double[] ys = {drawY + ps / 2.0, drawY + ps / 2.0, drawY - ps / 2.0};
                        // glow stroke
                        g.strokePolygon(xs, ys, 3);
                        // fill
                        g.setFill(fill);
                        g.fillPolygon(xs, ys, 3);
                        // thin outline
                        g.setLineWidth(1.0);
                        g.setStroke(baseStroke);
                        g.strokePolygon(xs, ys, 3);
                }
        }

        private static double clamp01(double v) {
                return (v < 0) ? 0 : (v > 1) ? 1 : v;
        }
}
