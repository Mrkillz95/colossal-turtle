package net.jj.colossalturtle.walk;

/**
 * How he walks: one leg at a time, front left, back right, front right, back left. Each step takes 12 seconds and
 * carries him about 300 blocks, so a full round of four moves him 1,200.
 */
public final class Gait {
    private Gait() {}

    public static final double CYCLE = 960, STRIDE = 1200;
    public static final double SWING = 0.25, LIFT = 140;
    private static final double[] OFFSET = {0, 0.75, 0.5, 0.25};

    public static final int CALM = 0, SCARED = 1, TIRED = 2, HIDING = 3;

    public static double speed(int mood) {
        return switch (mood) {
            case SCARED -> 1.7;
            case TIRED, HIDING -> 0;
            default -> 1;
        };
    }

    /** how far he sinks: lying down asleep, or pulled into his shell */
    public static double drop(int mood) {
        return switch (mood) {
            case TIRED -> 70;
            case HIDING -> 85;
            default -> 0;
        };
    }

    public static double phase(double cycle, int leg) {
        double p = cycle / CYCLE + OFFSET[leg];
        return p - Math.floor(p);
    }

    /** where the foot is, forward of the hip, at a point in its step */
    public static double footDx(double p) {
        double half = STRIDE * 0.375;
        if (p < SWING) {
            double t = p / SWING;
            return -half + 2 * half * (t * t * (3 - 2 * t));
        }
        return half - 2 * half * ((p - SWING) / (1 - SWING));
    }

    public static double lift(double p) {
        return p < SWING ? Math.sin(Math.PI * p / SWING) * LIFT : 0;
    }

    /** the foot is up */
    public static boolean swinging(double p) {
        return p < SWING;
    }

    /** a foot came down between these two moments */
    public static boolean planted(double before, double after, int leg) {
        if (after <= before) return false;
        double a = before / CYCLE + OFFSET[leg] - SWING, b = after / CYCLE + OFFSET[leg] - SWING;
        return Math.floor(b) > Math.floor(a);
    }
}
