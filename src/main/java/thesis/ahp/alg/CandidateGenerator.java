package thesis.ahp.alg;

import thesis.ahp.image.ImageUtil;
import thesis.ahp.jni.JpegComponent;
import thesis.ahp.jni.JpegImage;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandidateGenerator {
    private CandidateGenerator() {}

    public static List<Candidate> generate(Path pngPath, Path refJpegPath, JpegImage ref, int targetBits) throws Exception {
        BufferedImage png = ImageUtil.read(pngPath);
        BufferedImage refImg = ImageUtil.read(refJpegPath);
        double[][][] pngPlanes = ImageUtil.yCbCr(png);
        double[][][] refPlanes = ImageUtil.yCbCr(refImg);

        List<Candidate> out = collectCandidates(pngPlanes, refPlanes, ref);
        Collections.sort(out);
        return out;
    }

    private static List<Candidate> collectCandidates(double[][][] pngPlanes, double[][][] refPlanes, JpegImage ref) {
        List<Candidate> out = new ArrayList<>();
        for (int comp = 1; comp <= 2 && comp < ref.components.length; comp++) { // Cb and Cr only
            JpegComponent jc = ref.component(comp);
            double[] hist = ImageUtil.histogram(refPlanes[comp]);
            double channelWeight = (comp == 1) ? 0.3 : 0.5;

            for (int by = 0; by < jc.heightBlocks; by++) {
                for (int bx = 0; bx < jc.widthBlocks; bx++) {
                    int block = by * jc.widthBlocks + bx;
                    int px = bx * 8;
                    int py = by * 8;
                    if (py + 7 >= refPlanes[comp].length || px + 7 >= refPlanes[comp][0].length) continue;

                    for (int v = 2; v <= 5; v++) {
                        for (int u = 2; u <= 5; u++) {
                            int k = JpegMath.naturalIndex(u, v);
                            int qRef = jc.getCoeff(block, k);
                            if (qRef == 0) continue;

                            int val = ImageUtil.clamp((int) Math.round(refPlanes[comp][Math.min(py + 4, refPlanes[comp].length - 1)][Math.min(px + 4, refPlanes[comp][0].length - 1)]));
                            double wh = 1.0 / (1e-6 + hist[val]);
                            double[][] impact = JpegMath.idctBasis(u, v, Math.max(1, jc.quantTable[k]));
                            double side = sideInfo(pngPlanes[comp], refPlanes[comp], px, py);

                            double rhoPlus = SiUniwardCostEngine.rhoPlus(refPlanes[comp], px, py, impact);
                            double rhoMinus = SiUniwardCostEngine.rhoMinus(refPlanes[comp], px, py, impact);

                            // Side information lowers cost when PNG/reference mismatch suggests compression uncertainty.
                            double sideWeight = 1.0 / (1.0 + side);
                            rhoPlus = rhoPlus * wh * channelWeight * sideWeight;
                            rhoMinus = rhoMinus * wh * channelWeight * sideWeight;

                            // Avoid shrinkage to zero for nonzero-only candidate model.
                            if (qRef == 1) rhoMinus = 1e13;
                            if (qRef == -1) rhoPlus = 1e13;

                            out.add(new Candidate(comp, block, bx, by, u, v, k, qRef, rhoPlus, rhoMinus));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static double sideInfo(double[][] png, double[][] ref, int px, int py) {
        double s = 0.0;
        int n = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int yy = py + y;
                int xx = px + x;
                if (yy < png.length && xx < png[0].length && yy < ref.length && xx < ref[0].length) {
                    s += Math.abs(png[yy][xx] - ref[yy][xx]);
                    n++;
                }
            }
        }
        return n == 0 ? 0.0 : s / (255.0 * n);
    }
}
