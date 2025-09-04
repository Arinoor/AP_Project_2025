package play.controller;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import play.model.components.Link;
import play.model.core.Entity;
import play.model.engine.GameEngine;
import play.utils.WiringUtils;
import play.view.UiConstants;

import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Tool for adding/moving bends on existing links.
 * Hold SHIFT to enter bend mode:
 *  - SHIFT + click near a wire (segment) to add a bend (costs 1 coin the first time for that link).
 *  - SHIFT + drag an existing bend to move it (free).
 * Max 3 bends per link.
 * Hovering over a bend shows a yellow outline for feedback.
 *
 * Bends are stored in WiringUtils to keep geometry/budget/render/motion consistent.
 */
public class BendTool {

        private final GameEngine engine;
        private final List<Entity> entities;
        private final double systemSize;

        private final DoubleSupplier totalWireSupplier;
        private final DoubleSupplier usedWireSupplier;
        private final DoubleConsumer  usedWireUpdater;

        // Dragging state
        private Link dragLink = null;
        private int dragBendIndex = -1;
        private double dragCx, dragCy;
        // Hover state
        private Link hoverLink = null;
        private int hoverBendIndex = -1;

        public BendTool(GameEngine engine,
                        List<Entity> entities,
                        double systemSize,
                        DoubleSupplier totalWireSupplier,
                        DoubleSupplier usedWireSupplier,
                        DoubleConsumer usedWireUpdater) {
                this.engine = engine;
                this.entities = entities;
                this.systemSize = systemSize;
                this.totalWireSupplier = totalWireSupplier;
                this.usedWireSupplier = usedWireSupplier;
                this.usedWireUpdater  = usedWireUpdater;
        }

        public void attach(Canvas canvas) {
                canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
                canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
                canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
                canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        }

        private void onMouseMoved(MouseEvent e) {
                if (!e.isShiftDown()) {
                        hoverLink = null;
                        hoverBendIndex = -1;
                        return;
                }
                double pickR = UiConstants.BEND_DOT_RADIUS + 3.0;
                WiringUtils.BendHit hit = WiringUtils.findNearestBend(entities, e.getX(), e.getY(), pickR);
                if (hit != null) {
                        hoverLink = hit.link;
                        hoverBendIndex = hit.bendIndex;
                } else {
                        hoverLink = null;
                        hoverBendIndex = -1;
                }
        }

        private void onMousePressed(MouseEvent e) {
                if (!e.isShiftDown()) return;

                // Grab existing bend first
                double pickR = UiConstants.BEND_DOT_RADIUS + 3.0;
                WiringUtils.BendHit bendHit = WiringUtils.findNearestBend(entities, e.getX(), e.getY(), pickR);
                if (bendHit != null) {
                        dragLink = bendHit.link;
                        dragBendIndex = bendHit.bendIndex;
                        var b = WiringUtils.bends(dragLink).get(dragBendIndex); // NEW
                        dragCx = b.x; dragCy = b.y;                              // NEW
                        return;
                }


                // Otherwise, add a bend on nearest segment
                double tolerance = 6.0;
                WiringUtils.SegmentHit segHit = WiringUtils.findNearestSegment(entities, e.getX(), e.getY(), tolerance);
                if (segHit == null) return;

                var bends = WiringUtils.bends(segHit.link);
                boolean firstBend = bends.isEmpty();

                // Need coins for the first bend
                if (firstBend && engine.getCoins() <= 0) return;

                // Try to insert; reject if it would cross a system
                int insertedAt = WiringUtils.addBend(
                        segHit.link,
                        segHit.segmentIndex,
                        e.getX(), e.getY(),
                        entities,
                        systemSize
                );
                if (insertedAt >= 0) {
                        if (firstBend) engine.incrementCoins(-1); // charge exactly once per link
                        usedWireUpdater.accept(WiringUtils.totalWireLength(entities));

                        // Begin dragging newly inserted bend (snappy UX)
                        dragLink = segHit.link;
                        dragBendIndex = insertedAt;
                        dragCx = e.getX();               // NEW: clamp center at insertion
                        dragCy = e.getY();               // NEW
                }
        }

        private void onMouseDragged(MouseEvent e) {
                if (dragLink != null && dragBendIndex >= 0) {
                        // Clamp to a small fixed radius around drag center
                        double dx = e.getX() - dragCx;
                        double dy = e.getY() - dragCy;
                        double d  = Math.hypot(dx, dy);
                        double r  = UiConstants.BEND_DRAG_MAX_RADIUS;

                        double nx = (d > r && d > 1e-6) ? dragCx + dx * (r / d) : e.getX();
                        double ny = (d > r && d > 1e-6) ? dragCy + dy * (r / d) : e.getY();

                        boolean ok = WiringUtils.moveBend(
                                dragLink,
                                dragBendIndex,
                                nx, ny,
                                entities,
                                systemSize
                        );
                        if (ok) {
                                usedWireUpdater.accept(WiringUtils.totalWireLength(entities));
                        }
                }
        }


        private void onMouseReleased(MouseEvent e) {
                dragLink = null;
                dragBendIndex = -1;
        }

        /** Called from RenderSystem to draw the hover highlight if any. */
        public void renderHover(GraphicsContext g) {
                if (hoverLink != null && hoverBendIndex >= 0) {
                        var b = WiringUtils.bends(hoverLink).get(hoverBendIndex);
                        double r = UiConstants.BEND_DOT_RADIUS + 2;
                        g.setStroke(Color.YELLOW);
                        g.setLineWidth(2);
                        g.strokeOval(b.x - r, b.y - r, 2 * r, 2 * r);
                }
        }
}
