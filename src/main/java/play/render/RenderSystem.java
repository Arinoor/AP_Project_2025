package play.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import play.controller.BendTool;
import play.model.core.Entity;
import play.model.components.Link;
import play.model.components.PortInfo;
import play.model.components.Reference;
import play.model.components.Seed;
import play.model.components.Transform;
import play.model.systems.System;
import play.utils.WiringUtils;
import play.view.PacketView;
import play.view.UiConstants;
import javafx.scene.shape.ArcType;
import play.model.components.Disabled;
import play.model.constants.GameBalance;


import java.util.List;

public class RenderSystem implements System {

        private final List<Entity> entities;
        private final GraphicsContext g;

        // Wiring preview (controlled by MainController)
        private boolean previewActive = false;
        private Entity previewStartPort = null;
        private double previewX, previewY;

        // Optional: for drawing bend hover ring
        private BendTool bendTool;

        public RenderSystem(List<Entity> entities, GraphicsContext g) {
                this.entities = entities;
                this.g = g;
        }

        public void setBendTool(BendTool bendTool) {
                this.bendTool = bendTool;
        }

        /** Let the controller update the rubber-band preview while wiring. */
        public void updateWiringPreview(Entity startPort, double x, double y, boolean active) {
                this.previewStartPort = startPort;
                this.previewX = x;
                this.previewY = y;
                this.previewActive = active;
        }

        @Override
        public void update(double dt) { draw(); }

        private void draw() {
                // background
                g.setFill(Color.web("#12161c"));
                g.fillRect(0, 0, g.getCanvas().getWidth(), g.getCanvas().getHeight());

                // grid
                g.setStroke(Color.web("#1b222b"));
                g.setLineWidth(1);
                for (int x = 0; x < g.getCanvas().getWidth(); x += 20) g.strokeLine(x, 0, x, g.getCanvas().getHeight());
                for (int y = 0; y < g.getCanvas().getHeight(); y += 20) g.strokeLine(0, y, g.getCanvas().getWidth(), y);

                // links as curves (colored by shape of FROM port). Crossing -> red.
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);

                        // ⬇️ CHANGED: use the smooth curve points instead of straight control path
                        var pts = WiringUtils.curvePoints(l);
                        if (pts.size() < 2) continue;

                        boolean crosses = WiringUtils.crossesAnySystem(l, entities, UiConstants.SYSTEM_SIZE);

                        PortInfo.Shape shape = l.fromPort.get(PortInfo.class).shape;
                        Color linkColor = (shape == PortInfo.Shape.SQUARE) ? Color.web("#5dc2ff") : Color.web("#ff86a5");
                        if (crosses) linkColor = Color.web("#ff5f5f");

                        g.setStroke(linkColor);
                        g.setLineWidth(2.0);

                        for (int i = 0; i < pts.size() - 1; i++) {
                                var a = pts.get(i);
                                var b = pts.get(i + 1);
                                g.strokeLine(a.x, a.y, b.x, b.y);
                        }

                        // draw bends (visible colored dots)
                        var bends = WiringUtils.bends(l);
                        if (!bends.isEmpty()) {
                                g.setFill(!crosses ? Color.web("#40c7b5") : Color.web("#ff8a8a"));
                                for (var b : bends) {
                                        double r = UiConstants.BEND_DOT_RADIUS;
                                        g.fillOval(b.x - r, b.y - r, 2 * r, 2 * r);
                                        g.setStroke(Color.BLACK);
                                        g.setLineWidth(1);
                                        g.strokeOval(b.x - r, b.y - r, 2 * r, 2 * r);
                                }
                        }
                }

                // draw bend hover highlight (if any)
                if (bendTool != null) {
                        bendTool.renderHover(g);
                }

                // systems (rectangle + reference ring)
                final double SYSTEM_SIZE = UiConstants.SYSTEM_SIZE;
                for (Entity e : entities) {
                        if (!isSystemEntity(e)) continue;

                        Transform t = e.get(Transform.class);
                        double w = SYSTEM_SIZE, h = SYSTEM_SIZE;

                        boolean isDisabled = e.has(Disabled.class);
                        double remain = 0.0, ratio = 0.0;
                        if (isDisabled) {
                                remain = Math.max(0.0, e.get(Disabled.class).remaining);
                                ratio = GameBalance.SYSTEM_DISABLE_SECONDS > 1e-9
                                        ? Math.min(1.0, remain / GameBalance.SYSTEM_DISABLE_SECONDS)
                                        : 1.0;
                        }

                        // Base body
                        g.setFill(Color.web("#2a2f3a"));
                        g.fillRoundRect(t.x - w / 2, t.y - h / 2, w, h, 8, 8);
                        g.setStroke(Color.web("#3c4452"));
                        g.setLineWidth(1.0);
                        g.strokeRoundRect(t.x - w / 2, t.y - h / 2, w, h, 8, 8);

                        // Reference ring
                        if (e.has(Reference.class)) {
                                g.setStroke(Color.LIGHTGREEN);
                                g.setLineWidth(1.5);
                                g.strokeOval(t.x - 22, t.y - 22, 44, 44);
                        }

                        // ---- VISUAL CLUE WHEN DISABLED ----
                        if (isDisabled) {
                                // 1) Red translucent overlay
                                g.setGlobalAlpha(0.35);
                                g.setFill(Color.web("#ff3b30"));
                                g.fillRoundRect(t.x - w / 2, t.y - h / 2, w, h, 8, 8);
                                g.setGlobalAlpha(1.0);

                                // 2) Dashed red outline
                                g.setStroke(Color.web("#ff3b30"));
                                g.setLineWidth(2.0);
                                g.setLineDashes(6, 4);
                                g.strokeRoundRect(t.x - w / 2, t.y - h / 2, w, h, 8, 8);
                                g.setLineDashes(0); // reset

                                // 3) Cross mark
                                g.setLineWidth(1.5);
                                double pad = 6.0;
                                g.strokeLine(t.x - w / 2 + pad, t.y - h / 2 + pad, t.x + w / 2 - pad, t.y + h / 2 - pad);
                                g.strokeLine(t.x + w / 2 - pad, t.y - h / 2 + pad, t.x - w / 2 + pad, t.y + h / 2 - pad);

                                // 4) Countdown ring (shows remaining disable time)
                                double rr = (Math.max(w, h) * 0.6); // ring radius
                                double ringW = rr + 16;
                                double ringH = rr + 16;
                                g.setLineWidth(3.0);
                                // background ring
                                g.setStroke(Color.web("#4a1010"));
                                g.strokeOval(t.x - ringW / 2, t.y - ringH / 2, ringW, ringH);
                                // progress arc (clockwise from top)
                                g.setStroke(Color.web("#ffd1cc"));
                                g.setLineWidth(4.0);
                                g.strokeArc(
                                        t.x - ringW / 2, t.y - ringH / 2, ringW, ringH,
                                        90,                         // start at 12 o'clock
                                        -360.0 * ratio,             // clockwise sweep
                                        ArcType.OPEN
                                );
                        }
                }


                // ports
                final double ps = UiConstants.PORT_SIZE;
                for (Entity e : entities) {
                        if (!e.has(PortInfo.class) || !e.has(Transform.class)) continue;
                        PortInfo p = e.get(PortInfo.class);
                        Transform t = e.get(Transform.class);

                        if (p.shape == PortInfo.Shape.SQUARE) {
                                g.setFill(Color.web("#9ad9ff"));
                                g.fillRect(t.x - ps / 2, t.y - ps / 2, ps, ps);
                                g.setStroke(Color.web("#1f6aa5"));
                                g.strokeRect(t.x - ps / 2, t.y - ps / 2, ps, ps);
                        } else {
                                g.setFill(Color.web("#ffc1d0"));
                                double[] xs = {t.x - ps / 2, t.x + ps / 2, t.x};
                                double[] ys = {t.y + ps / 2, t.y + ps / 2, t.y - ps / 2};
                                g.fillPolygon(xs, ys, 3);
                                g.setStroke(Color.web("#a53d4e"));
                                g.strokePolygon(xs, ys, 3);
                        }
                }

                // seeds/packets
                for (Entity e : entities) {
                        if (!e.has(Seed.class) || !e.has(Transform.class)) continue;
                        Seed s = e.get(Seed.class);
                        Transform t = e.get(Transform.class);
                        PacketView.render(g, s, t.x, t.y);
                }

                // wiring preview on top
                if (previewActive && previewStartPort != null && previewStartPort.has(Transform.class)) {
                        Transform a = previewStartPort.get(Transform.class);
                        g.setStroke(Color.YELLOWGREEN);
                        g.setLineWidth(2.0);
                        g.strokeLine(a.x, a.y, previewX, previewY);
                }
        }

        private boolean isSystemEntity(Entity e) {
                return e.has(Transform.class)
                        && !e.has(PortInfo.class)
                        && !e.has(Seed.class)
                        && !e.has(Link.class);
        }
}
