package play.model.systems;

import play.model.components.PortInfo;
import play.model.components.Seed;

/** One source of truth for per-hop speed/accel rules. */
public final class Kinematics {
        private Kinematics() {}

        public static void applyForHop(Seed s, PortInfo.Shape outShape) {
                boolean compatible =
                        (s.type == Seed.Type.SQUARE  && outShape == PortInfo.Shape.SQUARE) ||
                                (s.type == Seed.Type.TRIANGLE && outShape == PortInfo.Shape.TRIANGLE);

                if (s.type == Seed.Type.SQUARE) {
                        double base = 120.0;
                        s.speed = compatible ? base * 0.5 : base; // half when compatible
                        s.accel = 0.0;
                } else { // TRIANGLE
                        s.speed = 140.0;
                        s.accel = compatible ? 0.0 : 220.0;       // accelerate when incompatible
                }
        }
}
