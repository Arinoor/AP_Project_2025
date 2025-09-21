package play.model.components;

import play.model.components.Link;
import play.model.core.Entity;

/** Packet (seed) travelling in the network. */
public class Seed {
        public enum Type {
                SQUARE, TRIANGLE, INFINITE, SECURE, PROTECTED, SECURE_PROTECTED, HEAVY, BITPACKET, HEAVY2
        }

        public Type type;

        // For HEAVY: number of units (size). Default for produced heavies is 8 (can be created smaller/larger by Merge).
        // For other types this field is ignored.
        public int heavySize = 8;

        // A color used for BITPACKET visuals (and for heavy->bitpacket color propagation).
        // Stored as 0xRRGGBB. 0 means unspecified (view falls back to default).
        public int colorRgb = 0;

        // kinematics
        public double speed = 120.0;     // px/s
        public double accel = 0.0;       // px/s^2
        /** Second acceleration (jerk), affects accel each frame: accel += jerk * dt. */
        public double jerk  = 0.0;       // px/s^3

        // legacy normalized progress [0..1] (derived from arc length / link length)
        public double progress = 0.0;

        // lateral offset (px) relative to wire; large magnitude => loss
        public double lateral = 0.0;

        // brief impact flash energy (also used as a short post-collision guard)
        public double impactEnergy = 0.0;

        // collision budgeting (separate from noise)
        public int collisions = 0;
        public int capacity;

        // cumulative "noise" (in PORT UNITS).
        // If noise > sizeUnits() => loss
        public double noise = 0.0;

        // the link this seed is currently traveling on (null when queued/inside a system)
        public Link currentLink = null;

        public boolean justCollided = false;

        /** If true, this seed is travelling back along its currentLink toward the source. */
        public boolean returning = false;

        // Phase 2: precise arc-length state along the currentLink polyline
        public double arcPos = 0.0; // [0..currentLink.length]

        // === PROTECTED specifics ===
        /** For PROTECTED: the original (primary) messaging type that entered the VPN (SQUARE/TRIANGLE/INFINITE). */
        public Type protectedBaseType = null;
        /** For PROTECTED: which messaging type's kinematics this seed emulates on each hop (SQUARE/TRIANGLE/INFINITE). */
        public Type emulateType = null;

        public Entity vpnConverter = null;

        public boolean hasBeenInSpySystem = false;

        public boolean trojan = false;

        public double heavy2DeviationAccumulator = 0;


        public Seed(Type type) {
                this.type = type;
                // Keep capacities: TRIANGLE=4, others=3 (PROTECTED adopts base capacity later if set)
                this.capacity = (type == Type.TRIANGLE) ? 4 : 3;

                // default for HEAVY: size 8, capacity can be large but keep as 3 for compatibility
                if (type == Type.HEAVY) {
                        this.heavySize = 8;
                        this.capacity = 3;
                }
                if (type == Type.HEAVY2) {
                        this.heavySize = 10; // Base unit size
                        this.capacity = 3;
                        this.speed = 80.0; // Constant speed like HEAVY
                }
                // BITPACKET: size units 1, capacity small
                if (type == Type.BITPACKET) {
                        this.capacity = 1;
                        // default speed overridden by kinematics when dispatched
                }
        }

        /**
         * Size in "port units".
         * square=2, triangle=3, infinite=1, secure=4.
         * PROTECTED = 2x of its primary (protectedBaseType).
         * HEAVY returns heavySize (variable).
         * BITPACKET = 1.
         */
        public double sizeUnits() {
                switch (type) {
                        case SQUARE:   return 2.0;
                        case TRIANGLE: return 3.0;
                        case INFINITE: return 1.0;
                        case SECURE:   return 4.0;
                        case PROTECTED:
                                double base = baseSizeUnitsFor(protectedBaseType);
                                return base * 2.0;
                        case SECURE_PROTECTED:  return 6.0;
                        case HEAVY:              return (double)Math.max(1, heavySize);
                        case HEAVY2: return 10.0;
                        case BITPACKET:          return 1.0;
                        default:
                                return 2.0;
                }
        }

        /** Helper: base sizeUnits for a given messaging type. */
        public static double baseSizeUnitsFor(Type t) {
                if (t == Type.TRIANGLE) return 3.0;
                if (t == Type.INFINITE) return 1.0;
                // treat null or others as square baseline
                return 2.0;
        }
}
