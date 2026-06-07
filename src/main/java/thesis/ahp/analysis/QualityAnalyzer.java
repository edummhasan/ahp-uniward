package thesis.ahp.analysis;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class QualityAnalyzer {

    public static QualityReport evaluate(
            String originalPath,
            String stegoPath,
            int payloadBits,
            int candidateCount,
            int qf
    ) throws Exception {
        return evaluate(originalPath, stegoPath, payloadBits, candidateCount, 0, 0, qf);
    }

    public static QualityReport evaluate(
            String originalPath,
            String stegoPath,
            int payloadBits,
            int candidateCount,
            int totalNonzeroAC,
            int totalCoefficients,
            int qf
    ) throws Exception {

        BufferedImage original = ImageIO.read(new File(originalPath));
        BufferedImage stego = ImageIO.read(new File(stegoPath));

        double mse = computeMSE(original, stego);
        double psnr = computePSNR(mse);
        double ssim = computeSSIM(original, stego);

        QualityReport r = new QualityReport();
        r.mse = mse;
        r.psnr = psnr;
        r.ssim = ssim;
        r.payloadBits = payloadBits;
        r.qf = qf;

        r.candidateBpnzAC = (double) payloadBits / Math.max(candidateCount, 1);
        r.overallBpnzAC = totalNonzeroAC > 0
                ? (double) payloadBits / totalNonzeroAC
                : 0.0;
        r.bitsPerTotalCoefficient = totalCoefficients > 0
                ? (double) payloadBits / totalCoefficients
                : 0.0;

        // Backward-compatible alias. This value is candidate-normalized.
        r.bpnzAC = r.candidateBpnzAC;

        return r;
    }

    // =============================
    // MSE
    // =============================
    private static double computeMSE(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();

        double mse = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                int rgb1 = a.getRGB(x, y);
                int rgb2 = b.getRGB(x, y);

                int r1 = (rgb1 >> 16) & 0xff;
                int g1 = (rgb1 >> 8) & 0xff;
                int b1 = rgb1 & 0xff;

                int r2 = (rgb2 >> 16) & 0xff;
                int g2 = (rgb2 >> 8) & 0xff;
                int b2 = rgb2 & 0xff;

                mse += sq(r1 - r2);
                mse += sq(g1 - g2);
                mse += sq(b1 - b2);
            }
        }

        return mse / (w * h * 3.0);
    }

    private static double sq(double x) {
        return x * x;
    }

    // =============================
    // PSNR
    // =============================
    private static double computePSNR(double mse) {
        if (mse == 0) return 99;
        return 10 * Math.log10((255 * 255) / mse);
    }

    // =============================
    // SSIM (simple version)
    // =============================
    private static double computeSSIM(BufferedImage a, BufferedImage b) {

        int w = a.getWidth();
        int h = a.getHeight();

        double meanA = 0, meanB = 0;

        int N = w * h;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                int rgb1 = a.getRGB(x, y);
                int rgb2 = b.getRGB(x, y);

                double gray1 = rgbToGray(rgb1);
                double gray2 = rgbToGray(rgb2);

                meanA += gray1;
                meanB += gray2;
            }
        }

        meanA /= N;
        meanB /= N;

        double varA = 0, varB = 0, cov = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                int rgb1 = a.getRGB(x, y);
                int rgb2 = b.getRGB(x, y);

                double g1 = rgbToGray(rgb1);
                double g2 = rgbToGray(rgb2);

                varA += sq(g1 - meanA);
                varB += sq(g2 - meanB);
                cov += (g1 - meanA) * (g2 - meanB);
            }
        }

        varA /= (N - 1);
        varB /= (N - 1);
        cov /= (N - 1);

        double C1 = 6.5025;
        double C2 = 58.5225;

        return ((2 * meanA * meanB + C1) * (2 * cov + C2)) /
                ((meanA * meanA + meanB * meanB + C1) *
                 (varA + varB + C2));
    }

    private static double rgbToGray(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;

        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
