package thesis.ahp.alg;

public final class RateDistortionSolver {
    private RateDistortionSolver() {}
    public static double entropy(double p) {
        if (p <= 1e-15 || p >= 1.0-1e-15) return 0.0;
        return -p * log2(p) - (1.0-p)*log2(1.0-p);
    }
    private static double log2(double x){ return Math.log(x)/Math.log(2.0); }
    public static double probability(double rho, double lambda) { return 1.0 / (1.0 + Math.exp(lambda * rho)); }
    public static double solveLambda(double[] costs, int targetBits) {
        double lo = 0.0, hi = 1.0;
        while (capacity(costs, hi) > targetBits && hi < 1e12) hi *= 2.0;
        for (int it=0; it<80; it++) {
            double mid = (lo + hi) / 2.0;
            if (capacity(costs, mid) > targetBits) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2.0;
    }
    public static double capacity(double[] costs, double lambda) {
        double s=0; for(double c:costs) s += entropy(probability(c, lambda)); return s;
    }
}
