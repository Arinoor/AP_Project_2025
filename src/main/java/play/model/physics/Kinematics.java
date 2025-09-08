package play.model.physics;

import play.model.components.PortInfo;
import play.model.components.Seed;
import play.model.constants.GameBalance;

/**
 * Per-hop entry kinematics based on OUT port shape and seed type.
 *
 * INFINITE behavior (requested):
 *  - On compatible (SQUARE) wire: constant acceleration (not constant speed),
 *    like TRIANGLE on an incompatible wire => use the same accel magnitude/sign.
 *  - On incompatible (TRIANGLE) wire: acceleration that decreases over time
 *    => assign a negative jerk so accel decays during the hop.
 */
public final class Kinematics {
        private Kinematics() {}

        public static void applyForHop(Seed s, PortInfo.Shape outShape) {
                // INFINITE has custom rules
                if (s.type == Seed.Type.INFINITE) {
                        boolean compatible = (outShape == PortInfo.Shape.SQUARE);

                        // Start each hop from a sane base speed
                        s.speed = GameBalance.SQUARE_BASE_SPEED;

                        if (compatible) {
                                // Constant acceleration (no jerk) — reuse triangle's "incompat" accel profile
                                // to match requested "like triangle on incompatible wire"
                                s.accel = GameBalance.TRIANGLE_INCOMPAT_ACCEL;
                                s.jerk  = 0.0;
                        } else {
                                // Incompatible: acceleration exists but decreases over time.
                                // Implement with negative jerk (accel += jerk * dt), clamping later.
                                double a0 = Math.abs(GameBalance.TRIANGLE_INCOMPAT_ACCEL) * 0.75; // initial accel
                                s.accel = a0;
                                s.jerk  = -a0 * 0.5; // decays to ~0 in ~2 seconds (tunable)
                        }
                        return;
                }

                // Unchanged behavior for other types
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
