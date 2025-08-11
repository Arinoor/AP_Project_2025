package play.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import play.components.PortInfo;
import play.components.Seed;
import play.components.Link;
import play.components.Transform;

public final class PacketView {
        private PacketView() {}

        // ports are ~12px; packets slightly larger (unchanged)
        private static final double PACKET_SIZE_SQ = 12.0; // square edge
        private static final double PACKET_SIZE_TR = 14.0; // triangle bounding box

        public static void render(GraphicsContext g, Seed s, double x, double y) {
                // draw subtle tether from wire to packet to make offset visible
                if (s.currentLink != null && s.lateral > 1.0) {
                        Transform a = s.currentLink.fromPort.get(Transform.class);
                        Transform b = s.currentLink.toPort.get(Transform.class);
                        // project (x,y) onto the link to find the on-wire point
                        double dx = b.x - a.x, dy = b.y - a.y;
                        double L2 = dx*dx + dy*dy;
                        if (L2 > 1e-6) {
                                double t = ((x - a.x) * dx + (y - a.y) * dy) / L2;
                                double px = a.x + dx * t;
                                double py = a.y + dy * t;
                                g.setStroke(Color.color(1,1,1, 0.25));
                                g.setLineWidth(1.0);
                                g.strokeLine(px, py, x, y);
                        }
                }

                // packet shapes (no outer ring/indicator)
                if (s.type == Seed.Type.SQUARE) {
                        g.setFill(Color.web("#9ad9ff"));
                        double half = PACKET_SIZE_SQ / 2.0;
                        g.fillRect(x - half, y - half, PACKET_SIZE_SQ, PACKET_SIZE_SQ);
                } else {
                        g.setFill(Color.web("#ffc1d0"));
                        double half = PACKET_SIZE_TR / 2.0;
                        double[] xs = {x - half, x + half, x};
                        double[] ys = {y + half, y + half, y - half};
                        g.fillPolygon(xs, ys, 3);
                }
        }
}
