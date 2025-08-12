package play.model.physics;

import play.model.components.PortInfo;
import play.model.components.Seed;
import play.model.constants.GameBalance;

public final class Kinematics {
        private Kinematics() {}

        /** Apply per-hop kinematics based on the OUT port shape and seed type. */
        public static void applyForHop(Seed s, PortInfo.Shape outShape) {
                boolean compatible =
                        (s.type == Seed.Type.SQUARE  && outShape == PortInfo.Shape.SQUARE) ||
                                (s.type == Seed.Type.TRIANGLE && outShape == PortInfo.Shape.TRIANGLE);

                if (s.type == Seed.Type.SQUARE) {
                        s.speed = compatible
                                ? GameBalance.SQUARE_BASE_SPEED * 0.5
                                : GameBalance.SQUARE_BASE_SPEED;
                        s.accel = 0.0;
                } else { // TRIANGLE
                        s.speed = GameBalance.TRIANGLE_BASE_SPEED;
                        s.accel = compatible ? 0.0 : GameBalance.TRIANGLE_INCOMPAT_ACCEL;
                }
        }
}
