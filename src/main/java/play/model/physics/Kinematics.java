package play.model.physics;

import play.model.components.PortInfo;
import play.model.components.Seed;
import play.model.constants.GameBalance;

/**
 * Per-hop entry kinematics based on OUT port shape and seed type.
 *
 * INFINITE: special accel/jerk rules already implemented earlier.
 * SECURE: no compatibility concept; constant speed on all wires (accel=jerk=0).
 */
public final class Kinematics {
        private Kinematics() {}

        // Reasonable base speed for SECURE (align with square base speed)
        private static final double SECURE_BASE_SPEED = GameBalance.SQUARE_BASE_SPEED;

        public static void applyForHop(Seed s, PortInfo.Shape outShape) {
                // === SECURE: constant speed, no accel/jerk, independent of port shape ===
                if (s.type == Seed.Type.SECURE) {
                        s.speed = SECURE_BASE_SPEED;
                        s.accel = 0.0;
                        s.jerk  = 0.0;
                        return;
                }

                // === INFINITE: previously customized behavior (kept) ===
                if (s.type == Seed.Type.INFINITE) {
                        boolean compatible = (outShape == PortInfo.Shape.SQUARE);

                        s.speed = GameBalance.SQUARE_BASE_SPEED; // base

                        if (compatible) {
                                // Constant acceleration (no jerk) — like triangle on incompatible
                                s.accel = GameBalance.TRIANGLE_INCOMPAT_ACCEL;
                                s.jerk  = 0.0;
                        } else {
                                // Incompatible: acceleration decreases over time (negative jerk)
                                double a0 = Math.abs(GameBalance.TRIANGLE_INCOMPAT_ACCEL) * 0.75;
                                s.accel = a0;
                                s.jerk  = -a0 * 0.5; // decays ~2s
                        }
                        return;
                }

                // === Original behavior for TRIANGLE / SQUARE ===
                boolean compatible =
                        (outShape == PortInfo.Shape.SQUARE   && s.type == Seed.Type.SQUARE) ||
                                (outShape == PortInfo.Shape.TRIANGLE && s.type == Seed.Type.TRIANGLE);

                if (s.type == Seed.Type.TRIANGLE) {
                        s.speed = GameBalance.TRIANGLE_BASE_SPEED;
                        s.accel = compatible ? 0.0 : GameBalance.TRIANGLE_INCOMPAT_ACCEL;
                        s.jerk  = 0.0;
                } else { // SQUARE
                        s.speed = compatible ? GameBalance.SQUARE_BASE_SPEED * 0.5
                                : GameBalance.SQUARE_BASE_SPEED;
                        s.accel = 0.0;
                        s.jerk  = 0.0;
                }
        }
}
