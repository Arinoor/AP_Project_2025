package play.utils;

public final class Vec2 {
        public double x;
        public double y;
        public Vec2(double x, double y) { this.x = x; this.y = y; }

        public Vec2 add(Vec2 o) { return new Vec2(x + o.x, y + o.y); }
        public Vec2 sub(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
        public Vec2 mul(double k){ return new Vec2(x * k, y * k); }
        public double dot(Vec2 o){ return x * o.x + y * o.y; }
        public double len()      { return Math.hypot(x, y); }
        public Vec2 norm()       { double L = len(); return (L <= 1e-9) ? new Vec2(0,0) : new Vec2(x/L, y/L); }

        public static double dist(Vec2 a, Vec2 b){ return Math.hypot(a.x - b.x, a.y - b.y); }
}
