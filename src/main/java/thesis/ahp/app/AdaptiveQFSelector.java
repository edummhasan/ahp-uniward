package thesis.ahp.app;

import thesis.ahp.alg.Candidate;
import thesis.ahp.alg.CandidateGenerator;
import thesis.ahp.image.ImageUtil;
import thesis.ahp.jni.JpegCore;
import thesis.ahp.jni.JpegImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AdaptiveQFSelector {
    private AdaptiveQFSelector() {}

    public static QFDecision choose(Path png, int payloadBits, int minQf, int maxQf) throws Exception {
        System.out.println("========== Adaptive QF Selection ==========");
        for (int qf = minQf; qf <= maxQf; qf++) {
            Path ref = Files.createTempFile("ahp-qf-" + qf + "-", ".jpg");
            try {
                ImageUtil.writeJpeg(ImageUtil.read(png), ref, qf);
                JpegImage refImg = JpegCore.readCoefficients(ref.toString());
                List<Candidate> candidates = CandidateGenerator.generate(png, ref, refImg, payloadBits);
                int stcCapacity = Math.max(0, candidates.size() - 32); // conservative for STC overhead/sync margin
                System.out.println("QF=" + qf + " | candidates=" + candidates.size() + " | conservative capacity=" + stcCapacity + " | required=" + payloadBits);
                if (stcCapacity >= payloadBits) {
                    System.out.println("Selected QF=" + qf);
                    System.out.println("===========================================");
                    return new QFDecision(qf, candidates.size(), stcCapacity, true);
                }
            } finally {
                Files.deleteIfExists(ref);
            }
        }
        System.out.println("No QF in range can fit the payload.");
        System.out.println("===========================================");
        return new QFDecision(maxQf, 0, 0, false);
    }

    public record QFDecision(int qf, int candidates, int stcCapacityBits, boolean fits) {}
}
