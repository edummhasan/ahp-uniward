package thesis.ahp.alg;

public final class SiUniwardCostEngine {
    private static final double SIGMA = 1.0;
    private static final double WET_COST = 1e13;

    private SiUniwardCostEngine() {}

    // Daubechies-8 low-pass decomposition filter coefficients used by UNIWARD-style residuals.
    private static final double[] DB8_LP = {
            -0.00011747678400228192,
             0.0006754494064505693,
            -0.00039174037337694705,
            -0.004870352993451574,
             0.008746094047405777,
             0.013981027917398282,
            -0.044088253930794755,
            -0.017369301001807547,
             0.128747426620186,
             0.0004724845739124,
            -0.2840155429615469,
            -0.015829105256023893,
             0.5853546836542159,
             0.6756307362972898,
             0.31287159091429995,
             0.05441584224310401
    };

    private static final double[] DB8_HP = highPassFromLowPass(DB8_LP);
    private static final double[][] FILTER_H = outer(DB8_HP, DB8_LP);
    private static final double[][] FILTER_V = outer(DB8_LP, DB8_HP);
    private static final double[][] FILTER_D = outer(DB8_HP, DB8_HP);
    private static final double[][][] FILTERS = { FILTER_H, FILTER_V, FILTER_D };

    public static double rhoPlus(double[][] plane, int pixelX, int pixelY, double[][] impactBasis) {
        return residualCost(plane, pixelX, pixelY, impactBasis);
    }

    public static double rhoMinus(double[][] plane, int pixelX, int pixelY, double[][] impactBasis) {
        double[][] neg = new double[impactBasis.length][impactBasis[0].length];
        for (int y = 0; y < impactBasis.length; y++) {
            for (int x = 0; x < impactBasis[0].length; x++) {
                neg[y][x] = -impactBasis[y][x];
            }
        }
        return residualCost(plane, pixelX, pixelY, neg);
    }

    public static double residualCost(double[][] plane, int pixelX, int pixelY, double[][] impactBasis) {
        double c = 0.0;
        for (double[][] filter : FILTERS) {
            c += filterImpact(plane, pixelX, pixelY, impactBasis, filter);
        }
        if (!Double.isFinite(c) || c <= 0) return WET_COST;
        return Math.min(c, WET_COST);
    }

    private static double filterImpact(double[][] plane, int px, int py, double[][] basis, double[][] f) {
        int h = plane.length;
        int w = plane[0].length;
        int fh = f.length;
        int fw = f[0].length;
        int ay = fh / 2;
        int ax = fw / 2;
        double sum = 0.0;

        for (int yy = -ay; yy < 8 + ay; yy++) {
            for (int xx = -ax; xx < 8 + ax; xx++) {
                int cy = py + yy;
                int cx = px + xx;
                if (cy < 0 || cy >= h || cx < 0 || cx >= w) continue;
                double r = 0.0;
                double dr = 0.0;
                for (int fy = 0; fy < fh; fy++) {
                    for (int fx = 0; fx < fw; fx++) {
                        int iy = cy + fy - ay;
                        int ix = cx + fx - ax;
                        if (iy >= 0 && iy < h && ix >= 0 && ix < w) {
                            r += plane[iy][ix] * f[fy][fx];
                        }
                        int by = iy - py;
                        int bx = ix - px;
                        if (by >= 0 && by < 8 && bx >= 0 && bx < 8) {
                            dr += basis[by][bx] * f[fy][fx];
                        }
                    }
                }
                sum += Math.abs(dr) / (SIGMA + Math.abs(r));
            }
        }
        return sum;
    }

    private static double[] highPassFromLowPass(double[] lp) {
        double[] hp = new double[lp.length];
        for (int i = 0; i < lp.length; i++) {
            hp[i] = ((i & 1) == 0 ? 1.0 : -1.0) * lp[lp.length - 1 - i];
        }
        return hp;
    }

    private static double[][] outer(double[] a, double[] b) {
        double[][] out = new double[a.length][b.length];
        for (int y = 0; y < a.length; y++) {
            for (int x = 0; x < b.length; x++) out[y][x] = a[y] * b[x];
        }
        return out;
    }
}
