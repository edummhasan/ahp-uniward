package thesis.ahp.alg;

public final class JpegMath {
    private JpegMath() {}

    // Libjpeg coefficient arrays are in natural 8x8 order, not zig-zag order.
    public static int naturalIndex(int u, int v) { return v * 8 + u; }
    public static int zigzagIndex(int u, int v) { return naturalIndex(u, v); }

    public static double[][] idctBasis(int u, int v, int q) {
        double[][] b = new double[8][8];
        double au = (u == 0) ? 1.0 / Math.sqrt(2.0) : 1.0;
        double av = (v == 0) ? 1.0 / Math.sqrt(2.0) : 1.0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                b[y][x] = 0.25 * au * av * q
                        * Math.cos(((2 * x + 1) * u * Math.PI) / 16.0)
                        * Math.cos(((2 * y + 1) * v * Math.PI) / 16.0);
            }
        }
        return b;
    }
}
