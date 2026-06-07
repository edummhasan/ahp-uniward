package thesis.ahp.analysis;

import thesis.ahp.jni.JpegComponent;
import thesis.ahp.jni.JpegImage;

public final class JpegCoefficientAnalyzer {

    private static final int HIST_MIN = -1024;
    private static final int HIST_MAX = 1024;
    private static final int HIST_SIZE = HIST_MAX - HIST_MIN + 1;
    private static final double EPS = 1e-12;

    private JpegCoefficientAnalyzer() {}

    public static JpegCoefficientReport compare(
            JpegImage reference,
            JpegImage stego,
            int payloadBits,
            int selectedQF,
            int candidateCount,
            int conservativeCapacity,
            boolean embeddingSuccess,
            boolean extractionSuccess,
            boolean aesGcmVerified
    ) {
        JpegCoefficientReport r = new JpegCoefficientReport();

        r.payloadBits = payloadBits;
        r.selectedQF = selectedQF;
        r.candidateCount = candidateCount;
        r.conservativeCapacity = conservativeCapacity;

        r.embeddingSuccess = embeddingSuccess;
        r.extractionSuccess = extractionSuccess;
        r.aesGcmVerified = aesGcmVerified;

        int[] histRef = new int[HIST_SIZE];
        int[] histStego = new int[HIST_SIZE];

        int components = Math.min(reference.components.length, stego.components.length);

        for (int c = 0; c < components; c++) {
            JpegComponent refComp = reference.components[c];
            JpegComponent stegoComp = stego.components[c];

            int len = Math.min(refComp.coefficients.length, stegoComp.coefficients.length);

            for (int i = 0; i < len; i++) {
                int coeffIndex = i % 64;

                int a = refComp.coefficients[i];
                int b = stegoComp.coefficients[i];

                r.totalCoefficients++;

                if (coeffIndex != 0 && a != 0) {
                    r.totalNonzeroAC++;
                }

                histRef[toBin(a)]++;
                histStego[toBin(b)]++;

                if (a != b) {
                    r.modifiedCoefficients++;

                    if (c == 0) {
                        r.modifiedY++;
                    } else if (c == 1) {
                        r.modifiedCb++;
                    } else if (c == 2) {
                        r.modifiedCr++;
                    }

                    if (a != 0 && b != 0) {
                        r.modifiedNonzeroToNonzero++;
                    } else if (a == 0 && b != 0) {
                        r.modifiedZeroToNonzero++;
                    } else if (a != 0 && b == 0) {
                        r.modifiedNonzeroToZero++;
                    }
                }
            }
        }

        r.candidateBpnzAC = payloadBits / Math.max(1.0, candidateCount);
        r.overallBpnzAC = payloadBits / Math.max(1.0, r.totalNonzeroAC);
        r.bitsPerTotalCoefficient = payloadBits / Math.max(1.0, r.totalCoefficients);

        // Backward-compatible alias. This value is candidate-normalized, not global.
        r.bpnzAC = r.candidateBpnzAC;

        r.modificationRateAllCoefficients =
                r.modifiedCoefficients / Math.max(1.0, r.totalCoefficients);

        r.modificationRateCandidates =
                r.modifiedCoefficients / Math.max(1.0, candidateCount);

        double[] p = normalize(histRef);
        double[] q = normalize(histStego);

        r.dctHistogramL1 = histogramL1(p, q);
        r.dctKLDivergence = klDivergence(p, q);

        return r;
    }

    private static int toBin(int v) {
        int clipped = Math.max(HIST_MIN, Math.min(HIST_MAX, v));
        return clipped - HIST_MIN;
    }

    private static double[] normalize(int[] hist) {
        double total = 0.0;

        for (int v : hist) {
            total += v;
        }

        double[] p = new double[hist.length];

        for (int i = 0; i < hist.length; i++) {
            p[i] = hist[i] / Math.max(total, EPS);
        }

        return p;
    }

    private static double histogramL1(double[] a, double[] b) {
        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }

        return sum;
    }

    private static double klDivergence(double[] p, double[] q) {
        double sum = 0.0;

        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0.0) {
                sum += p[i] * Math.log(p[i] / (q[i] + EPS));
            }
        }

        return sum;
    }
}
