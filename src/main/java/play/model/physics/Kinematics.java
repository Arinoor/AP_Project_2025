package play.model.physics;

import play.model.components.PortInfo;
import play.model.components.Seed;
import play.model.constants.GameBalance;

/**
 * Per-hop entry kinematics based on OUT port shape and seed type.
 *
 * INFINITE: custom accel/jerk rules (compatible square -> constant accel; incompatible -> decaying accel).
 * SECURE: constant speed on all wires (accel=jerk=0); adaptive slow handled in QueueSystem.
 * PROTECTED: delegates to its emulateType (SQUARE/TRIANGLE/INFINITE) for motion rules.
 */
public final class Kinematics {
        private Kinematics() {}

        private static final double SECURE_BASE_SPEED = GameBalance.SQUARE_BASE_SPEED;

        public static void applyForHop(Seed s, PortInfo.Shape outShape) {
                // === SECURE: constant speed, no accel/jerk, ignores port shape for kinematics ===
                if (s.type == Seed.Type.SECURE) {
                        s.speed = SECURE_BASE_SPEED;
                        s.accel = 0.0;
                        s.jerk  = 0.0;
                        return;
                }

                // === PROTECTED: behave like one of the messenger types (hidden) ===
                if (s.type == Seed.Type.PROTECTED) {
                        Seed.Type emu = (s.emulateType != null) ? s.emulateType : Seed.Type.SQUARE;
                        applyForHopMessenger(s, outShape, emu);
                        return;
                }

                // === Native messenger types ===
                applyForHopMessenger(s, outShape, s.type);
        }

        /** Apply kinematics for a specific messenger type (SQUARE/TRIANGLE/INFINITE). */
        private static void applyForHopMessenger(Seed s, PortInfo.Shape outShape, Seed.Type messengerType) {
                if (messengerType == Seed.Type.INFINITE) {
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

                boolean compatible =
                        (outShape == PortInfo.Shape.SQUARE   && messengerType == Seed.Type.SQUARE) ||
                                (outShape == PortInfo.Shape.TRIANGLE && messengerType == Seed.Type.TRIANGLE);

                if (messengerType == Seed.Type.TRIANGLE) {
                        s.speed = GameBalance.TRIANGLE_BASE_SPEED;
                        s.accel = compatible ? 0.0 : GameBalance.TRIANGLE_INCOMPAT_ACCEL;
                        s.jerk  = 0.0;
                } else { // SQUARE (default baseline)
                        s.speed = compatible ? GameBalance.SQUARE_BASE_SPEED * 0.5
                                : GameBalance.SQUARE_BASE_SPEED;
                        s.accel = 0.0;
                        s.jerk  = 0.0;
                }
        }
}
