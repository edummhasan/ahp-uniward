package thesis.ahp.analysis;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public final class ImageQualityAnalyzer {

    private static final double EPS = 1e-12;

    private ImageQualityAnalyzer() {}

    public static ImageComparisonMetrics compare(
            String label,
            String imageAPath,
            String imageBPath
    ) throws Exception {

        BufferedImage a = ImageIO.read(new File(imageAPath));
        BufferedImage b = ImageIO.read(new File(imageBPath));

        if (a == null) {
            throw new IllegalArgumentException("Cannot read image A: " + imageAPath);
        }

        if (b == null) {
            throw new IllegalArgumentException("Cannot read image B: " + imageBPath);
        }

        a = toRgb(a);
        b = toRgb(b);

        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            b = resizeTo(b, a.getWidth(), a.getHeight());
        }

        int[] histA = grayscaleHistogram(a);
        int[] histB = grayscaleHistogram(b);

        double[] pA = normalize(histA);
        double[] pB = normalize(histB);

        ImageComparisonMetrics r = new ImageComparisonMetrics();

        r.label = label;
        r.mse = mse(a, b);
        r.psnr = psnr(r.mse);
        r.ssim = windowedSsim(a, b);

        r.entropyA = entropy(pA);
        r.entropyB = entropy(pB);
        r.entropyDiff = Math.abs(r.entropyA - r.entropyB);

        r.histogramL1 = histogramL1(pA, pB);
        r.klDivergence = klDivergence(pA, pB);

        File fA = new File(imageAPath);
        File fB = new File(imageBPath);

        r.fileSizeA = fA.length();
        r.fileSizeB = fB.length();
        r.fileSizeDiff = r.fileSizeB - r.fileSizeA;
        r.fileSizeRatio = r.fileSizeA == 0 ? 0.0 : (double) r.fileSizeB / r.fileSizeA;

        return r;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(
                src.getWidth(),
                src.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        return out;
    }

    private static BufferedImage resizeTo(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        return out;
    }

    private static double mse(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();

        double total = 0.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbA = a.getRGB(x, y);
                int rgbB = b.getRGB(x, y);

                int rA = (rgbA >> 16) & 0xff;
                int gA = (rgbA >> 8) & 0xff;
                int bA = rgbA & 0xff;

                int rB = (rgbB >> 16) & 0xff;
                int gB = (rgbB >> 8) & 0xff;
                int bB = rgbB & 0xff;

                total += sq(rA - rB);
                total += sq(gA - gB);
                total += sq(bA - bB);
            }
        }

        return total / (w * h * 3.0);
    }

    private static double psnr(double mse) {
        if (mse <= 0.0) {
            return 99.0;
        }

        return 10.0 * Math.log10((255.0 * 255.0) / mse);
    }

    private static double windowedSsim(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();

        int window = 11;
        int half = window / 2;

        double c1 = 6.5025;
        double c2 = 58.5225;

        double total = 0.0;
        int count = 0;

        for (int y = half; y < h - half; y += 4) {
            for (int x = half; x < w - half; x += 4) {

                double meanA = 0.0;
                double meanB = 0.0;
                int n = 0;

                for (int dy = -half; dy <= half; dy++) {
                    for (int dx = -half; dx <= half; dx++) {
                        meanA += gray(a.getRGB(x + dx, y + dy));
                        meanB += gray(b.getRGB(x + dx, y + dy));
                        n++;
                    }
                }

                meanA /= n;
                meanB /= n;

                double varA = 0.0;
                double varB = 0.0;
                double cov = 0.0;

                for (int dy = -half; dy <= half; dy++) {
                    for (int dx = -half; dx <= half; dx++) {
                        double gA = gray(a.getRGB(x + dx, y + dy));
                        double gB = gray(b.getRGB(x + dx, y + dy));

                        varA += sq(gA - meanA);
                        varB += sq(gB - meanB);
                        cov += (gA - meanA) * (gB - meanB);
                    }
                }

                varA /= (n - 1);
                varB /= (n - 1);
                cov /= (n - 1);

                double numerator =
                        (2 * meanA * meanB + c1) *
                        (2 * cov + c2);

                double denominator =
                        (meanA * meanA + meanB * meanB + c1) *
                        (varA + varB + c2);

                total += numerator / denominator;
                count++;
            }
        }

        return count == 0 ? 1.0 : total / count;
    }

    private static int[] grayscaleHistogram(BufferedImage img) {
        int[] hist = new int[256];

        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = (int) Math.round(gray(img.getRGB(x, y)));
                g = clamp(g, 0, 255);
                hist[g]++;
            }
        }

        return hist;
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

    private static double entropy(double[] p) {
        double h = 0.0;

        for (double v : p) {
            if (v > 0.0) {
                h -= v * log2(v);
            }
        }

        return h;
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

    private static double gray(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;

        return 0.299 * r + 0.587 * g + 0.114 * b;
    }

    private static double sq(double x) {
        return x * x;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
