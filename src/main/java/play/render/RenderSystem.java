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

                // links as polylines (colored by shape of FROM port). Crossing -> red.
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        var pts = WiringUtils.path(l);
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

                        g.setFill(Color.web("#2a2f3a"));
                        g.fillRoundRect(t.x - w / 2, t.y - h / 2, w, h, 8, 8);
                        g.setStroke(Color.web("#3c4452"));
                        g.strokeRoundRect(t.x - w / 2, t.y - h / 2, w, h, 8, 8);

                        if (e.has(Reference.class)) {
                                g.setStroke(Color.LIGHTGREEN);
                                g.strokeOval(t.x - 22, t.y - 22, 44, 44);
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
