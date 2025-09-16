package play.model.components;

import play.model.core.Entity;
import play.utils.Vec2;
import play.model.constants.GameBalance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A connection between two ports, now modeled as a polyline:
 * startPort -> [bend0, bend1, ...] -> endPort
 * Length is the sum of segment lengths; helpers provide arc-length queries.
 */
public class Link {
        public final Entity fromPort;
        public final Entity toPort;

        /** Bend points in scene coordinates. Max: GameBalance.MAX_BENDS_PER_LINK. */
        private final List<Vec2> bends = new ArrayList<>();

        /** Precomputed cumulative segment lengths (monotonic, last == total length). */
        private final List<Double> cum = new ArrayList<>();

        private double length = 1.0;


        /** Cached polyline points (start + bends + end) computed at last updateGeometry(). */
        private final List<Vec2> poly = new ArrayList<>();

        /** Optional validity flag set by wiring validation (e.g., crossing systems). */
        private boolean valid = true;


        /** New: count of HEAVY packets that have passed over this link. When this reaches
         * GameBalance.HEAVY_MAX_PASSES the link should be destroyed.
         */
        private int heavyPassCount = 0;

        public Link(Entity fromPort, Entity toPort) {
                this.fromPort = fromPort;
                this.toPort   = toPort;
                updateGeometry(); // initializes poly, cum, length
        }

        // ------- Bends API -------

        public List<Vec2> getBends() { return Collections.unmodifiableList(bends); }

        public boolean canAddBend() {
                return bends.size() < GameBalance.MAX_BENDS_PER_LINK;
        }

        /** Adds a bend (cost handled by controller). Returns index or -1 if cap reached. */
        public int addBend(Vec2 p) {
                if (!canAddBend()) return -1;
                bends.add(p);
                updateGeometry();
                return bends.size() - 1;
        }

        public void setBend(int idx, Vec2 p) {
                if (idx < 0 || idx >= bends.size()) return;
                bends.set(idx, p);
                updateGeometry();
        }

        public void removeBend(int idx) {
                if (idx < 0 || idx >= bends.size()) return;
                bends.remove(idx);
                updateGeometry();
        }

        public void clearBends() {
                bends.clear();
                updateGeometry();
        }

        // ------- Geometry / length -------

        /** Rebuild the polyline from current port transforms and bends; recompute arc-length table. */
        public void updateGeometry() {
                poly.clear();
                cum.clear();

                // Start
                Vec2 a = centerOf(fromPort);
                // End
                Vec2 b = centerOf(toPort);

                poly.add(a);
                poly.addAll(bends);
                poly.add(b);

                double acc = 0.0;
                cum.add(acc);
                for (int i = 1; i < poly.size(); i++) {
                        acc += Vec2.dist(poly.get(i-1), poly.get(i));
                        cum.add(acc);
                }
                length = Math.max(1.0, acc);
        }

        private static Vec2 centerOf(Entity port) {
                if (port == null || !port.has(Transform.class)) return new Vec2(0,0);
                Transform t = port.get(Transform.class);
                return new Vec2(t.x, t.y);
        }

        /** Total polyline length. */
        public double length() { return length; }

        /** All polyline points (start + bends + end). */
        public List<Vec2> polyline() { return Collections.unmodifiableList(poly); }

        /** Position at arc-length s (clamped) along the polyline. */
        public Vec2 pointAtArc(double s) {
                if (poly.size() == 1) return poly.get(0);
                if (s <= 0) return poly.get(0);
                if (s >= length) return poly.get(poly.size()-1);

                // Find segment by cumulative lengths (linear scan; small N)
                int seg = 0;
                while (seg < cum.size()-1 && cum.get(seg+1) < s) seg++;
                double s0 = cum.get(seg), s1 = cum.get(seg+1);
                double t  = (s - s0) / Math.max(1e-9, (s1 - s0));

                Vec2 p0 = poly.get(seg);
                Vec2 p1 = poly.get(seg+1);
                return new Vec2(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t);
        }

        /** Tangent (unit) at arc-length s. */
        public Vec2 tangentAtArc(double s) {
                if (poly.size() < 2) return new Vec2(1,0);
                if (s <= 0) return poly.get(1).sub(poly.get(0)).norm();
                if (s >= length) {
                        int n = poly.size();
                        return poly.get(n-1).sub(poly.get(n-2)).norm();
                }
                int seg = 0;
                while (seg < cum.size()-1 && cum.get(seg+1) < s) seg++;
                return poly.get(seg+1).sub(poly.get(seg)).norm();
        }

        /** Convert 0..1 logical progress to arc-length. */
        public double arcFromProgress(double progress) {
                double p = (progress < 0) ? 0 : (progress > 1) ? 1 : progress;
                return p * length;
        }

        /** Convert arc-length to 0..1 progress. */
        public double progressFromArc(double s) {
                if (length <= 1e-9) return 0.0;
                double clamped = Math.max(0.0, Math.min(length, s));
                return clamped / length;
        }

        // ------- Validity flag (set by wiring validation) -------

        public boolean isValid() { return valid; }
        public void setValid(boolean v) { this.valid = v; }

        // Convenience: do quick rect intersection test against each segment (used by WiringService).
        public boolean anySegmentIntersectsRect(double rx, double ry, double rw, double rh) {
                // rectangle edges
                for (int i = 0; i < poly.size()-1; i++) {
                        Vec2 p = poly.get(i);
                        Vec2 q = poly.get(i+1);
                        if (segmentIntersectsRect(p.x, p.y, q.x, q.y, rx, ry, rw, rh)) return true;
                }
                return false;
        }

        private static boolean segmentIntersectsRect(double x1, double y1, double x2, double y2,
                                                     double rx, double ry, double rw, double rh) {
                // Liang–Barsky or Cohen–Sutherland would be ideal; here use a quick test:
                // 1) If either endpoint inside rect -> count as intersect.
                if (x1 >= rx && x1 <= rx+rw && y1 >= ry && y1 <= ry+rh) return true;
                if (x2 >= rx && x2 <= rx+rw && y2 >= ry && y2 <= ry+rh) return true;

                // 2) Segment vs each rect edge
                return linesIntersect(x1,y1,x2,y2, rx,ry, rx+rw,ry)     || // top
                        linesIntersect(x1,y1,x2,y2, rx,ry, rx,ry+rh)     || // left
                        linesIntersect(x1,y1,x2,y2, rx,ry+rh, rx+rw,ry+rh)|| // bottom
                        linesIntersect(x1,y1,x2,y2, rx+rw,ry, rx+rw,ry+rh);  // right
        }

        private static boolean linesIntersect(double x1,double y1,double x2,double y2,
                                              double x3,double y3,double x4,double y4) {
                double d = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4);
                if (Math.abs(d) < 1e-9) return false; // parallel or coincident (ignore)
                double t = ((x1-x3)*(y3-y4) - (y1-y3)*(x3-x4)) / d;
                double u = ((x1-x3)*(y1-y2) - (y1-y3)*(x1-x2)) / d;
                return t >= 0 && t <= 1 && u >= 0 && u <= 1;
        }

        public int heavyPassCount() { return heavyPassCount; }
        /** Increment and return new count. */
        public int incrementHeavyPassCount() { return ++heavyPassCount; }
}
