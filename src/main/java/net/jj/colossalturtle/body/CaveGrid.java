package net.jj.colossalturtle.body;

/**
 * The cave noise for one chunk, worked out every 4 blocks and blended in between (like the game does for its own
 * caves), which is much quicker than working it out for every block of his insides.
 */
public final class CaveGrid {
    public static final ThreadLocal<CaveGrid> CURRENT = new ThreadLocal<>();

    private final int x0, z0, y0, ny;
    private final double[] a, b, c;

    public CaveGrid(int x0, int z0, int yLo, int yHi) {
        this.x0 = x0;
        this.z0 = z0;
        this.y0 = Math.floorDiv(yLo, 4) * 4;
        this.ny = (Math.floorDiv(yHi, 4) * 4 - y0) / 4 + 2;
        a = new double[5 * 5 * ny];
        b = new double[a.length];
        c = new double[a.length];
        for (int i = 0; i < 5; i++)
            for (int k = 0; k < 5; k++)
                for (int j = 0; j < ny; j++) {
                    int n = (i * 5 + k) * ny + j;
                    double x = x0 + i * 4, y = y0 + j * 4, z = z0 + k * 4;
                    a[n] = TurtleShape.caveA(x, y, z);
                    b[n] = TurtleShape.caveB(x, y, z);
                    c[n] = TurtleShape.caveC(x, y, z);
                }
    }

    public boolean covers(double x, double y, double z) {
        return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16 && y >= y0 && y < y0 + (ny - 1) * 4;
    }

    public boolean cave(double x, double y, double z) {
        double fx = (x - x0) / 4, fy = (y - y0) / 4, fz = (z - z0) / 4;
        int i = (int) fx, j = (int) fy, k = (int) fz;
        double tx = fx - i, ty = fy - j, tz = fz - k;
        return TurtleShape.hollow(lerp(a, i, j, k, tx, ty, tz), lerp(b, i, j, k, tx, ty, tz), lerp(c, i, j, k, tx, ty, tz));
    }

    private double lerp(double[] v, int i, int j, int k, double tx, double ty, double tz) {
        double c00 = mix(v[at(i, j, k)], v[at(i + 1, j, k)], tx), c10 = mix(v[at(i, j + 1, k)], v[at(i + 1, j + 1, k)], tx);
        double c01 = mix(v[at(i, j, k + 1)], v[at(i + 1, j, k + 1)], tx), c11 = mix(v[at(i, j + 1, k + 1)], v[at(i + 1, j + 1, k + 1)], tx);
        return mix(mix(c00, c10, ty), mix(c01, c11, ty), tz);
    }

    private int at(int i, int j, int k) {
        return (i * 5 + k) * ny + j;
    }

    private static double mix(double p, double q, double t) {
        return p + (q - p) * t;
    }
}
