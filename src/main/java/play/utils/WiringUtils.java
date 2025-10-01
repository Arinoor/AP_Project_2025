package play.utils;

import play.model.components.Link;
import play.model.components.PortInfo;
import play.model.components.Seed;
import play.model.components.Transform;
import play.model.core.Entity;

import java.util.*;

/**
 * WiringUtils
 * ----------
 * Stateless/static helpers for bendable-connection geometry:
 *  - Stores up to 3 bend points per Link (without modifying Link).
 *  - Computes polyline paths, lengths, sampling along the path.
 *  - Hit-tests for selecting segments and bend handles.
 *  - Detects crossing with system rectangles.
 *
 * No JavaFX types are used here; UI code should live elsewhere (e.g., BendTool).
 */
public final class WiringUtils {



        private WiringUtils() {}

        /** Immutable point holder to avoid JavaFX dependency. */
        public static final class Pt {
                public double x, y;
                public Pt(double x, double y) { this.x = x; this.y = y; }
        }

        /** Identity-keyed bend store so Link stays unmodified. */
        private static final IdentityHashMap<Link, List<Pt>> BENDS = new IdentityHashMap<>();

        private static final int SAMPLES_PER_SEGMENT = 20;


        /** Max bends per link (phase rule). */
        public static final int MAX_BENDS_PER_LINK = 3;

        /** Reset all stored bends (e.g., on level reload). */
        public static void clearAllBends() { BENDS.clear(); }

        /** Returns the (mutable) bend list for this link; creates if absent. */
        public static List<Pt> bends(Link l) {
                return BENDS.computeIfAbsent(l, k -> new ArrayList<>(0));
        }

        /** Path = [fromPort] + bends + [toPort]. */
        public static List<Pt> path(Link l) {
                Transform a = l.fromPort.get(Transform.class);
                Transform b = l.toPort  .get(Transform.class);
                List<Pt> pts = new ArrayList<>();
                pts.add(new Pt(a.x, a.y));
                pts.addAll(bends(l));
                pts.add(new Pt(b.x, b.y));
                return pts;
        }

        /** Total polyline length for a link. */
        public static double pathLength(Link l) {
                List<Pt> pts = curvePoints(l);
                if (pts.size() < 2) return 0.0;

                double len = 0.0;
                for (int i = 0; i < pts.size() - 1; i++) {
                        Pt a = pts.get(i), b = pts.get(i + 1);
                        len += Math.hypot(b.x - a.x, b.y - a.y);
                }
                return len;
        }

        /** Sum of polyline lengths for all Link entities in the scene. */
        public static double totalWireLength(List<Entity> entities) {
                double sum = 0.0;
                for (Entity e : entities) if (e.has(Link.class)) sum += pathLength(e.get(Link.class));
                return sum;
        }

        // ----------------- Hit testing -----------------

        /** Hit on a link's segment (segmentIndex is index of left endpoint in path()). */
        public static final class SegmentHit {
                public final Link link;
                public final int segmentIndex;  // 0..N-2 for path of N points
                public final double distance;
                public final double hitX, hitY;
                public SegmentHit(Link link, int segmentIndex, double distance, double hitX, double hitY) {
                        this.link = link; this.segmentIndex = segmentIndex; this.distance = distance;
                        this.hitX = hitX; this.hitY = hitY;
                }
        }

        /** Hit on an existing bend handle. */
        public static final class BendHit {
                public final Link link;
                public final int bendIndex;     // 0..(bends.size-1)
                public final double distance;
                public BendHit(Link link, int bendIndex, double distance) {
                        this.link = link; this.bendIndex = bendIndex; this.distance = distance;
                }
        }

        /** Find nearest segment across all links within maxDist pixels. */
        public static SegmentHit findNearestSegment(List<Entity> entities, double x, double y, double maxDist) {
                SegmentHit best = null;
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        List<Pt> pts = path(l);
                        for (int i = 0; i + 1 < pts.size(); i++) {
                                Pt a = pts.get(i), b = pts.get(i + 1);
                                double[] pr = projectPointToSegment(a.x, a.y, b.x, b.y, x, y);
                                double dist = pr[2];
                                if (dist <= maxDist && (best == null || dist < best.distance)) {
                                        best = new SegmentHit(l, i, dist, pr[0], pr[1]);
                                }
                        }
                }
                return best;
        }

        /** Find nearest bend handle across all links within maxDist pixels. */
        public static BendHit findNearestBend(List<Entity> entities, double x, double y, double maxDist) {
                BendHit best = null;
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        Link l = e.get(Link.class);
                        List<Pt> bs = bends(l);
                        for (int i = 0; i < bs.size(); i++) {
                                Pt p = bs.get(i);
                                double d = Math.hypot(p.x - x, p.y - y);
                                if (d <= maxDist && (best == null || d < best.distance)) {
                                        best = new BendHit(l, i, d);
                                }
                        }
                }
                return best;
        }

        // ----------------- Bends editing -----------------

        /**
         * Insert a bend after the given segment index (between path[i] and path[i+1]).
         * Returns the inserted bend index in bends(), or -1 on failure.
         * Does NOT charge coins; UI/controller should handle economy.
         */
        public static int addBend(Link link,
                                  int segmentIndex,
                                  double x, double y,
                                  List<Entity> entities,
                                  double systemSize) {
                var bends = BENDS.computeIfAbsent(link, k -> new ArrayList<>());
                if (bends.size() >= MAX_BENDS_PER_LINK) return -1;

                // Clamp insert position into [0, bends.size()]
                int insertAt = Math.max(0, Math.min(segmentIndex, bends.size()));
                bends.add(insertAt, new Pt(x, y));

                // NOTE: No crossing check here anymore; editor must allow fixing red/invalid wires.
                return insertAt;
        }


        /**
         * Move an existing bend to (x,y). Returns true if kept; false if reverted
         * due to crossing a system. Economy/budget should be handled by caller.
         */
        public static boolean moveBend(Link link,
                                       int bendIndex,
                                       double x, double y,
                                       List<Entity> entities,
                                       double systemSize) {
                var bends = BENDS.get(link);
                if (bends == null || bendIndex < 0 || bendIndex >= bends.size()) return false;

                Pt b = bends.get(bendIndex);
                b.x = x;
                b.y = y;

                // NOTE: No crossing check here either; allow moves to fix invalid routing.
                return true;
        }


        // ----------------- Path sampling -----------------

        /**
         * Sample position along the polyline at normalized progress t in [0..1].
         * Note: uses arc-length parameterization internally so packet motion is smooth.
         */
        public static Pt pointAlongNormalized(Link l, double t) {
                List<Pt> pts = curvePoints(l);
                if (pts.isEmpty()) return new Pt(0, 0);
                if (pts.size() == 1) return pts.get(0);

                t = Math.max(0.0, Math.min(1.0, t));

                // Build cumulative arc-length table
                int n = pts.size();
                double[] cum = new double[n];
                cum[0] = 0.0;
                for (int i = 1; i < n; i++) {
                        Pt a = pts.get(i - 1), b = pts.get(i);
                        cum[i] = cum[i - 1] + Math.hypot(b.x - a.x, b.y - a.y);
                }
                double total = cum[n - 1];
                if (total <= 1e-9) return pts.get(0);

                double target = t * total;

                // Find segment where target falls
                int hi = Arrays.binarySearch(cum, target);
                if (hi < 0) hi = -hi - 1;
                hi = Math.min(Math.max(1, hi), n - 1);
                int lo = hi - 1;

                double segLen = cum[hi] - cum[lo];
                double local = (segLen <= 1e-9) ? 0.0 : (target - cum[lo]) / segLen;

                Pt A = pts.get(lo), B = pts.get(hi);
                return new Pt(A.x + (B.x - A.x) * local,
                        A.y + (B.y - A.y) * local);
        }


        // ----------------- Crossing checks -----------------

        /** Any link crosses any system rect (excluding the two endpoint systems)? */
        public static boolean hasAnySystemCrossing(List<Entity> entities, double systemSizePx) {
                for (Entity e : entities) {
                        if (!e.has(Link.class)) continue;
                        if (crossesAnySystem(e.get(Link.class), entities, systemSizePx)) return true;
                }
                return false;
        }

        /** Checks a single link against all system rects (excluding its endpoint systems). */
        public static boolean crossesAnySystem(Link l, List<Entity> entities, double systemSizePx) {
                Entity sysA = l.fromPort.get(PortInfo.class).parentSystem;
                Entity sysB = l.toPort  .get(PortInfo.class).parentSystem;

                List<Pt> pts = path(l);

                for (Entity e : entities) {
                        if (!isSystemEntity(e)) continue;
                        if (e == sysA || e == sysB) continue;

                        Transform t = e.get(Transform.class);
                        double half = systemSizePx / 2.0;
                        double left = t.x - half, right = t.x + half, top = t.y - half, bottom = t.y + half;

                        for (int i = 0; i + 1 < pts.size(); i++) {
                                Pt a = pts.get(i), b = pts.get(i + 1);
                                if (segmentIntersectsRect(a.x, a.y, b.x, b.y, left, top, right, bottom)) return true;
                        }
                }
                return false;
        }

        private static boolean isSystemEntity(Entity e) {
                return e.has(Transform.class)
                        && !e.has(PortInfo.class)
                        && !e.has(Seed.class)
                        && !e.has(Link.class);
        }

        // ----------------- Geometry helpers -----------------

        /** Project (x,y) onto segment AB; returns {hx, hy, distance}. */
        private static double[] projectPointToSegment(double ax, double ay, double bx, double by, double x, double y) {
                double vx = bx - ax, vy = by - ay;
                double wx = x - ax,  wy = y - ay;
                double vv = vx * vx + vy * vy;
                double t = (vv <= 1e-9) ? 0 : (wx * vx + wy * vy) / vv;
                if (t < 0) t = 0; else if (t > 1) t = 1;
                double hx = ax + vx * t, hy = ay + vy * t;
                double d = Math.hypot(x - hx, y - hy);
                return new double[]{hx, hy, d};
        }

        private static boolean segmentIntersectsRect(double x1, double y1, double x2, double y2,
                                                     double left, double top, double right, double bottom) {
                // Quick reject: both points on same outside side
                if (x1 < left && x2 < left) return false;
                if (x1 > right && x2 > right) return false;
                if (y1 < top && y2 < top) return false;
                if (y1 > bottom && y2 > bottom) return false;

                // Endpoint inside -> count as intersect
                if (x1 > left && x1 < right && y1 > top && y1 < bottom) return true;
                if (x2 > left && x2 < right && y2 > top && y2 < bottom) return true;

                // Edge intersections
                return segmentsIntersect(x1, y1, x2, y2, left, top, right, top) ||
                        segmentsIntersect(x1, y1, x2, y2, right, top, right, bottom) ||
                        segmentsIntersect(x1, y1, x2, y2, right, bottom, left, bottom) ||
                        segmentsIntersect(x1, y1, x2, y2, left, bottom, left, top);
        }

        private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                                 double cx, double cy, double dx, double dy) {
                double d1 = direction(cx, cy, dx, dy, ax, ay);
                double d2 = direction(cx, cy, dx, dy, bx, by);
                double d3 = direction(ax, ay, bx, by, cx, cy);
                double d4 = direction(ax, ay, bx, by, dx, dy);
                if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                        && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;
                // collinear touch
                return onSegment(cx, cy, dx, dy, ax, ay) || onSegment(cx, cy, dx, dy, bx, by)
                        || onSegment(ax, ay, bx, by, cx, cy) || onSegment(ax, ay, bx, by, dx, dy);
        }

        /** Returns control points: [fromPort, bends..., toPort] */
        private static List<Pt> controlPoints(Link l) {
                List<Pt> cps = new ArrayList<>();
                Pt a = portCenter(l.fromPort);
                Pt b = portCenter(l.toPort);
                cps.add(a);
                cps.addAll(bends(l)); // existing accessor to per-link bend list
                cps.add(b);
                return cps;
        }

        /** Helper to read a port's current world position (center). */
        private static Pt portCenter(Entity port) {
                Transform t = port.get(Transform.class);
                return new Pt(t.x, t.y);
        }

        /** Standard Catmull–Rom (uniform, tension = 0.5) */
        private static Pt catmull(Pt p0, Pt p1, Pt p2, Pt p3, double u) {
                double u2 = u * u;
                double u3 = u2 * u;
                double x =
                        0.5 * ((2 * p1.x) +
                                (-p0.x + p2.x) * u +
                                (2*p0.x - 5*p1.x + 4*p2.x - p3.x) * u2 +
                                (-p0.x + 3*p1.x - 3*p2.x + p3.x) * u3);
                double y =
                        0.5 * ((2 * p1.y) +
                                (-p0.y + p2.y) * u +
                                (2*p0.y - 5*p1.y + 4*p2.y - p3.y) * u2 +
                                (-p0.y + 3*p1.y - 3*p2.y + p3.y) * u3);
                return new Pt(x, y);
        }

        /**
         * Duplicates end points to keep the spline clamped to endpoints,
         * and samples per control-point segment.
         */
        public static List<Pt> curvePoints(Link l) {
                List<Pt> cps = controlPoints(l);
                List<Pt> out = new ArrayList<>();
                if (cps.size() < 2) return out;

                // Degenerate straight line
                if (cps.size() == 2) {
                        out.add(cps.get(0));
                        out.add(cps.get(1));
                        return out;
                }

                // Build padded list [P-1, P0, ..., Pn, Pn+1]
                List<Pt> pad = new ArrayList<>(cps.size() + 2);
                pad.add(cps.get(0));
                pad.addAll(cps);
                pad.add(cps.get(cps.size() - 1));

                for (int i = 1; i < pad.size() - 2; i++) {
                        Pt p0 = pad.get(i - 1), p1 = pad.get(i), p2 = pad.get(i + 1), p3 = pad.get(i + 2);
                        for (int s = 0; s < SAMPLES_PER_SEGMENT; s++) {
                                double u = (double) s / SAMPLES_PER_SEGMENT;
                                if (i == 1 && s == 0) out.add(new Pt(p1.x, p1.y)); // exact hit at start of segment
                                out.add(catmull(p0, p1, p2, p3, u));
                        }
                }
                // Ensure exact last point
                out.add(cps.get(cps.size() - 1));
                return out;
        }

        private static double direction(double ax, double ay, double bx, double by, double px, double py) {
                return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        }

        private static boolean onSegment(double ax, double ay, double bx, double by, double px, double py) {
                return Math.min(ax, bx) - 1e-9 <= px && px <= Math.max(ax, bx) + 1e-9 &&
                        Math.min(ay, by) - 1e-9 <= py && py <= Math.max(ay, by) + 1e-9 &&
                        Math.abs(direction(ax, ay, bx, by, px, py)) <= 1e-9;
        }
}
