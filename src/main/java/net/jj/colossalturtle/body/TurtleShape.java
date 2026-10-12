package net.jj.colossalturtle.body;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * His body, in his own frame: x points where he walks, z to his right, y is height in the shell world where the
 * ground he stands on is at G. The shell world's blocks and the far-off look both come from here.
 */
public final class TurtleShape {
    private TurtleShape() {}

    public static final int G = 64;

    // shell
    public static final double HL = 3000, HW = 2500;
    public static final double RIM = 150, DOME = 560, BOT_C = 85, BOT_E = 125;
    public static final double SHELL_T = 45, PLAST_T = 25;
    public static final double PLATE = 380, GROOVE = 16;

    // legs, in walking order: front left, back right, front right, back left
    public static final double HIP_X = 1900, HIP_Z = 2000, LEG_R = 330, LEG_R_TOP = 280, PAD = 24;
    public static final double[][] HIPS = {{HIP_X, -HIP_Z}, {-HIP_X, HIP_Z}, {HIP_X, HIP_Z}, {-HIP_X, -HIP_Z}};

    // neck, head, tail
    public static final double NECK_X0 = 2700, NECK_X1 = 4300, NECK_W = 240, NECK_H = 140;
    public static final double HEAD_X = 4950, HEAD_Y = G + 240, HEAD_A = 720, HEAD_B = 230, HEAD_C = 520;
    public static final double BEAK_X = 5560;
    public static final double EYE_X = 5200, EYE_Z = 330, EYE_R = 85;
    public static final double NOSE_X = 5450, NOSE_Z = 70, NOSE_Y = G + 300, NOSE_R = 9;
    public static final double TAIL_X0 = -2850, TAIL_X1 = -3950;

    // inside
    public static final double STOMACH_Y = G + 300, ST_A = 900, ST_B = 130, ST_C = 650;
    public static final double STOMACH_WATER = STOMACH_Y - ST_B * 0.45;
    public static final double THROAT_R = 10;
    private static final double[][] THROAT = {{5700, G + 178}, {5350, G + 178}, {4300, G + 215}, {2800, G + 215}, {850, G + 300}};

    // the whole of him, for quick "is this near him" checks
    public static final double FRONT = HEAD_X + HEAD_A, BACK = TAIL_X1, SIDE = Math.max(HW, HIP_Z + LEG_R + PAD + 40);

    private static final SimplexNoise N1 = new SimplexNoise(new LegacyRandomSource(9151L));
    private static final SimplexNoise N2 = new SimplexNoise(new LegacyRandomSource(4417L));
    private static final SimplexNoise N3 = new SimplexNoise(new LegacyRandomSource(2203L));

    public static final class Column {
        public double x, z;
        // shell
        public boolean shell;
        public double r, top, bottom, edge, plateDist, plateCx, plateCz;
        public long plate;
        public boolean groove, crack;
        public int ruinH;
        // legs
        public int leg = -1;
        public double legDist, legDx, legDz, legTop;
        // neck, head, tail (NaN where there's none)
        public double neckLo, neckHi, headLo, headHi, tailLo, tailHi;
        public double eye;
        // the lowest and highest body block here, NaN when the column misses him
        public double lowest, highest;
    }

    public static double sq(double v) {
        return v * v;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static long hash(long a, long b, long c) {
        long x = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        x *= 0x94D049BB133111EBL;
        return x ^ x >>> 32;
    }

    public static double rnd(long a, long b, long c) {
        return (hash(a, b, c) >>> 11) * 0x1.0p-53;
    }

    public static double noise(int which, double x, double z) {
        return (which == 1 ? N1 : which == 2 ? N2 : N3).getValue(x, z);
    }

    public static double noise3(int which, double x, double y, double z) {
        return (which == 1 ? N1 : which == 2 ? N2 : N3).getValue(x, y, z);
    }

    public static Column column(double x, double z) {
        return column(x, z, new Column());
    }

    public static Column column(double x, double z, Column c) {
        c.x = x;
        c.z = z;
        c.shell = false;
        c.leg = -1;
        c.ruinH = 0;
        c.groove = c.crack = false;
        c.neckLo = c.neckHi = c.headLo = c.headHi = c.tailLo = c.tailHi = Double.NaN;
        c.eye = -1;
        c.lowest = Double.POSITIVE_INFINITY;
        c.highest = Double.NEGATIVE_INFINITY;

        c.r = Math.sqrt(sq(x / HL) + sq(z / HW));
        if (c.r <= 1) shell(c);

        for (int i = 0; i < 4; i++) {
            double dx = x - HIPS[i][0], dz = z - HIPS[i][1];
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < LEG_R + PAD + 40) {
                c.leg = i;
                c.legDist = d;
                c.legDx = dx;
                c.legDz = dz;
                double top = c.shell ? c.bottom + PLAST_T : G + BOT_E + 22 - 20 * sq(d / LEG_R);
                c.legTop = top;
                c.lowest = Math.min(c.lowest, G);
                c.highest = Math.max(c.highest, top);
                break;
            }
        }

        if (x > NECK_X0 && x < NECK_X1 + 80) {
            double t = clamp((x - NECK_X0) / (NECK_X1 - NECK_X0), 0, 1);
            double cy = G + 185 + 45 * t, w = NECK_W * (1 - 0.15 * t);
            if (Math.abs(z) < w) {
                double h = NECK_H * Math.sqrt(1 - sq(z / w));
                c.neckLo = cy - h;
                c.neckHi = Math.min(cy + h, cy + NECK_H * 0.75);
                c.lowest = Math.min(c.lowest, c.neckLo);
                c.highest = Math.max(c.highest, c.neckHi);
            }
        }

        double hx = (x - HEAD_X) / HEAD_A;
        if (Math.abs(hx) < 1) {
            double cz = HEAD_C * (hx > 0 ? 1 - 0.35 * hx * hx : 1);
            double s = 1 - hx * hx - sq(z / cz);
            if (s > 0) {
                double h = HEAD_B * Math.sqrt(s);
                c.headLo = HEAD_Y - h * 0.8;
                c.headHi = HEAD_Y + h;
                double e = Math.sqrt(sq(x - EYE_X) + sq(Math.abs(z) - EYE_Z));
                if (e < EYE_R + 6) c.eye = e;
                c.lowest = Math.min(c.lowest, c.headLo);
                c.highest = Math.max(c.highest, c.headHi);
            }
        }

        if (x < TAIL_X0 + 60 && x > TAIL_X1) {
            double t = clamp((TAIL_X0 - x) / (TAIL_X0 - TAIL_X1), 0, 1);
            double w = 320 * (1 - t) + 70 * t;
            if (Math.abs(z) < w) {
                double hm = 110 * (1 - t) + 14;
                double h = hm * Math.sqrt(1 - sq(z / w));
                double cy = G + 8 + 120 * Math.pow(1 - t, 1.5);
                c.tailLo = Math.max(G, cy - h);
                c.tailHi = cy + h * 0.7;
                c.lowest = Math.min(c.lowest, c.tailLo);
                c.highest = Math.max(c.highest, c.tailHi);
            }
        }

        if (c.lowest == Double.POSITIVE_INFINITY) c.lowest = c.highest = Double.NaN;
        return c;
    }

    private static void shell(Column c) {
        double x = c.x, z = c.z, r = c.r;
        c.shell = true;
        plates(x, z, c);
        double q = 1 - r * r;
        double top = G + RIM + DOME * Math.pow(q, 0.55);
        double fade = clamp((0.9 - r) / 0.1, 0, 1);
        top += fade * (9 * clamp(1 - c.plateDist / PLATE, 0, 1) + 4 * N1.getValue(x / 90, z / 90));
        c.groove = fade > 0 && c.edge < GROOVE;
        if (c.groove) top -= fade * 24 * (1 - sq(c.edge / GROOVE));
        if (r > 0.86) top += 3 * N1.getValue(x / 40, z / 40);
        c.crack = r < 0.7 && !c.groove && Math.abs(N2.getValue(x / 300, z / 300)) < 0.012;
        c.top = top;
        c.bottom = G + BOT_C + (BOT_E - BOT_C) * r * r;
        c.lowest = c.bottom;
        c.highest = top;
        // a ruin on some of the inner plates
        if (r < 0.8 && !c.groove && rnd(c.plate, 7, 0) < 0.1) {
            double d = Math.sqrt(sq(x - c.plateCx) + sq(z - c.plateCz));
            double ring = 10 + 12 * rnd(c.plate, 8, 0);
            double a = Math.atan2(z - c.plateCz, x - c.plateCx);
            int seg = (int) Math.floor((a + Math.PI) / (Math.PI * 2) * 24);
            if (Math.abs(d - ring) < 1.2 && rnd(c.plate, seg, 9) > 0.3) c.ruinH = 3 + (int) (7 * rnd(c.plate, seg, 10));
            if (d < 1.6) c.ruinH = 6 + (int) (8 * rnd(c.plate, 11, 0));
            if (c.ruinH > 0) c.highest = top + c.ruinH;
        }
    }

    // plates: cells around jittered points, edge = how far to the line between two plates
    private static void plates(double x, double z, Column c) {
        double gx = x / PLATE, gz = z / PLATE;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double f1 = 1e9, f2 = 1e9, bx = 0, bz = 0;
        long best = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int cx = ix + dx, cz = iz + dz;
                long h = hash(cx, cz, 31);
                double px = cx + 0.15 + 0.7 * ((h >>> 11 & 0xFFFFF) / (double) 0xFFFFF);
                double pz = cz + 0.15 + 0.7 * ((h >>> 33 & 0xFFFFF) / (double) 0xFFFFF);
                double d = Math.sqrt(sq(gx - px) + sq(gz - pz));
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                    best = h;
                    bx = px;
                    bz = pz;
                } else if (d < f2) f2 = d;
            }
        c.edge = (f2 - f1) * 0.5 * PLATE;
        c.plate = best;
        c.plateDist = f1 * PLATE;
        c.plateCx = bx * PLATE;
        c.plateCz = bz * PLATE;
    }

    /** leg radius at a height, ledges every 7 blocks and a wide toe pad at the bottom */
    public static double legRadius(Column c, int y) {
        double f = clamp((y - G) / Math.max(1, c.legTop - G), 0, 1);
        double r = LEG_R + (LEG_R_TOP - LEG_R) * f;
        if ((y - G) % 7 == 0) r += 2;
        if (y <= G + 3) r = Math.max(r, LEG_R + PAD);
        return r;
    }

    public static boolean inStomach(double x, double y, double z) {
        return stomach(x, y, z) < 1;
    }

    public static double stomach(double x, double y, double z) {
        return sq(x / ST_A) + sq((y - STOMACH_Y) / ST_B) + sq(z / ST_C);
    }

    /** height of the middle of his throat at x, or NaN past either end */
    public static double throatY(double x) {
        for (int i = 0; i + 1 < THROAT.length; i++) {
            double x0 = THROAT[i][0], x1 = THROAT[i + 1][0];
            if (x <= x0 && x >= x1) {
                double t = (x0 - x) / (x0 - x1);
                return THROAT[i][1] + (THROAT[i + 1][1] - THROAT[i][1]) * t;
            }
        }
        return Double.NaN;
    }

    public static boolean inThroat(double x, double y, double z) {
        if (Math.abs(z) > THROAT_R) return false;
        double cy = throatY(x);
        return !Double.isNaN(cy) && sq(z) + sq(y - cy) < THROAT_R * THROAT_R;
    }

    public static boolean inNostril(double x, double y, double z) {
        return x > NOSE_X && sq(Math.abs(z) - NOSE_Z) + sq(y - NOSE_Y) < NOSE_R * NOSE_R;
    }

    public static boolean inMouth(double x, double y, Column c) {
        return x > 5350 && y >= G + 172 && y <= G + 184 && !Double.isNaN(c.headLo) && Math.abs(c.z) < 200;
    }

    public static boolean cave(double x, double y, double z) {
        CaveGrid g = CaveGrid.CURRENT.get();
        if (g != null && g.covers(x, y, z)) return g.cave(x, y, z);
        return hollow(caveA(x, y, z), caveB(x, y, z), caveC(x, y, z));
    }

    public static boolean hollow(double a, double b, double c) {
        return a * a + b * b < 0.012 || c > 0.62;
    }

    public static double caveA(double x, double y, double z) {
        return N1.getValue(x / 90, y / 45, z / 90);
    }

    public static double caveB(double x, double y, double z) {
        return N2.getValue(x / 90, y / 45, z / 90);
    }

    public static double caveC(double x, double y, double z) {
        return N3.getValue(x / 260, y / 120, z / 260);
    }

    /** close to any part of him, with some room to spare */
    public static boolean near(double x, double z, double margin) {
        if (x > FRONT + margin || x < BACK - margin || Math.abs(z) > SIDE + margin) return false;
        if (sq(x / (HL + margin)) + sq(z / (HW + margin)) <= 1) return true;
        for (double[] h : HIPS) if (sq(x - h[0]) + sq(z - h[1]) < sq(LEG_R + PAD + 40 + margin)) return true;
        if (x > NECK_X0 && Math.abs(z) < Math.max(NECK_W, HEAD_C) + margin) return true;
        return x < TAIL_X0 + margin && Math.abs(z) < 300 + margin;
    }

    // which of his lands a spot is in
    public static Zone zone(double x, double y, double z) {
        Column c = column(x, z);
        if (inStomach(x, y, z)) return Zone.STOMACH;
        if (c.leg >= 0 && y < c.legTop + 4) return Zone.LEG_CLIFFS;
        if (c.shell) {
            if (y < c.bottom) return Zone.UNDERHANG;
            if (y < c.top - SHELL_T) return Zone.INSIDE;
            if (c.r > 0.86) return Zone.RIM_FOREST;
            if (c.edge < GROOVE + 6 && c.r < 0.9) return Zone.MOSS_VALLEYS;
            return Zone.HIGHLANDS;
        }
        if (x >= NECK_X0 && x < NECK_X1 && Math.abs(z) < 400) return Zone.NECK_BRIDGE;
        if (x >= NECK_X1 && Math.abs(z) < 900) return Zone.HEAD_LANDS;
        if (x <= TAIL_X0 && Math.abs(z) < 600) return Zone.TAIL_MARSH;
        return Zone.RIM_FOREST;
    }
}
