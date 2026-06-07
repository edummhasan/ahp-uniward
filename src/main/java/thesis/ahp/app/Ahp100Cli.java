package thesis.ahp.app;

import thesis.ahp.alg.Candidate;
import thesis.ahp.alg.CandidateGenerator;
import thesis.ahp.analysis.EvaluationCsvWriter;
import thesis.ahp.analysis.EvaluationSummary;
import thesis.ahp.analysis.FullEvaluationAnalyzer;
import thesis.ahp.image.ImageUtil;
import thesis.ahp.jni.JpegComponent;
import thesis.ahp.jni.JpegCore;
import thesis.ahp.jni.JpegImage;
import thesis.ahp.payload.PayloadPackage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Ahp100Cli {

    private static final int MIN_QF = 75;
    private static final int MAX_QF = 100;
    private static final int STC_HEIGHT = 10;
    private static final int HEADER_BITS = PayloadPackage.headerBytes() * 8;

    private Ahp100Cli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            return;
        }

        String mode = args[0];
        Map<String, String> o = parse(Arrays.copyOfRange(args, 1, args.length));

        switch (mode) {
            case "embed" -> embed(
                    Path.of(req(o, "--png")),
                    Path.of(req(o, "--msgFile")),
                    Path.of(req(o, "--out")),
                    req(o, "--password")
            );

            case "extract" -> extract(
                    Path.of(req(o, "--png")),
                    Path.of(req(o, "--stego")),
                    req(o, "--password")
            );

            case "make-ref" -> makeReferenceJpeg(
                    Path.of(req(o, "--png")),
                    Path.of(req(o, "--out")),
                    Integer.parseInt(o.getOrDefault("--qf", "85"))
            );

            case "inspect" -> inspect(Path.of(req(o, "--jpg")));

            case "capacity" -> CapacityInspector.inspect(req(o, "--jpg"));

            case "scan-qf" -> scanQfCapacity(Path.of(req(o, "--png")));

            default -> usage();
        }
    }

    private static void embed(Path png, Path msg, Path out, String password) throws Exception {
        byte[] message = Files.readAllBytes(msg);

        /*
         * Trial payload is packed with MAX_QF only to estimate the final payload size.
         * The payload length remains stable because QF is stored as a fixed-size field.
         */
        byte[] trialPayload = PayloadPackage.pack(message, password, MAX_QF);
        int requiredBits = trialPayload.length * 8;

        AdaptiveQFSelector.QFDecision decision =
                AdaptiveQFSelector.choose(png, requiredBits, MIN_QF, MAX_QF);

        if (!decision.fits()) {
            /*
             * Batch-safe behavior:
             * If no QF in the allowed range can carry the payload, this image-message
             * pair is skipped instead of throwing an exception. This prevents a batch
             * script from stopping on one unsuitable image and allows processing to
             * continue with the next image.
             */
            System.out.println("SKIPPED: No feasible QF in range "
                    + MIN_QF + "-" + MAX_QF
                    + " can fit the payload.");
            System.out.println("Required bits: " + requiredBits);
            System.out.println("Input PNG: " + png.toAbsolutePath());
            System.out.println("Message file: " + msg.toAbsolutePath());
            System.out.println("Output stego JPEG was not created: " + out.toAbsolutePath());
            Files.deleteIfExists(out);
            System.out.println("Embedding success: false");
            System.out.println("Extraction success: false");
            System.out.println("AES-GCM verified: false");
            System.out.println("Reason: insufficient adaptive-QF capacity.");
            return;
        }

        int selectedQF = decision.qf();

        Path ref = Path.of("debug_ref_qf" + selectedQF + ".jpg");
        ImageUtil.writeJpeg(ImageUtil.read(png), ref, selectedQF);

        JpegImage refImg = JpegCore.readCoefficients(ref.toString());

        byte[] payload = PayloadPackage.pack(message, password, selectedQF);
        int payloadBits = payload.length * 8;

        List<Candidate> candidates =
                CandidateGenerator.generate(png, ref, refImg, payloadBits);

        int conservativeCapacity = Math.max(0, candidates.size() - 32);

        if (candidates.size() < payloadBits) {
            /*
             * Defensive batch-safe check. This situation should normally not happen
             * after adaptive-QF selection, but if it does, skip this image-message
             * pair rather than terminating the whole batch.
             */
            System.out.println("SKIPPED: Selected QF did not provide enough candidates for STC.");
            System.out.println("Selected QF: " + selectedQF);
            System.out.println("Payload bits: " + payloadBits);
            System.out.println("Candidates: " + candidates.size());
            System.out.println("Input PNG: " + png.toAbsolutePath());
            System.out.println("Message file: " + msg.toAbsolutePath());
            System.out.println("Output stego JPEG was not created: " + out.toAbsolutePath());
            Files.deleteIfExists(out);
            System.out.println("Embedding success: false");
            System.out.println("Extraction success: false");
            System.out.println("AES-GCM verified: false");
            System.out.println("Reason: insufficient selected-candidate capacity.");
            return;
        }

        StcPipelineIntegration stc = new StcPipelineIntegration(STC_HEIGHT);

        byte[] header = Arrays.copyOfRange(payload, 0, PayloadPackage.headerBytes());
        byte[] body = Arrays.copyOfRange(payload, PayloadPackage.headerBytes(), payload.length);

        List<Candidate> headerCandidates =
                candidates.subList(0, HEADER_BITS);

        List<Candidate> bodyCandidates =
                candidates.subList(HEADER_BITS, HEADER_BITS + body.length * 8);

        byte[] headerCover = coverBits(refImg, headerCandidates);
        byte[] headerStego =
                stc.embedPayload(headerCover, costs(headerCandidates), header);
        applyParityChanges(refImg, headerCandidates, headerCover, headerStego);

        byte[] bodyCover = coverBits(refImg, bodyCandidates);
        byte[] bodyStego =
                stc.embedPayload(bodyCover, costs(bodyCandidates), body);
        applyParityChanges(refImg, bodyCandidates, bodyCover, bodyStego);

        JpegCore.writeCoefficients(ref.toString(), out.toString(), refImg);

        System.out.println("Adaptive QF selected: " + selectedQF);
        long totalCoefficients = countTotalCoefficients(refImg);
        long totalNonzeroAC = countTotalNonzeroAC(refImg);

        double candidateBpnzAC = payloadBits / Math.max(1.0, candidates.size());
        double overallBpnzAC = payloadBits / Math.max(1.0, totalNonzeroAC);
        double bitsPerTotalCoefficient = payloadBits / Math.max(1.0, totalCoefficients);

        System.out.println("Payload bits: " + payloadBits);
        System.out.println("Candidates Cb/Cr: " + candidates.size());
        System.out.println("Conservative STC capacity: " + conservativeCapacity);
        System.out.printf("Candidate-normalized bpnzAC: %.6f%n", candidateBpnzAC);
        System.out.printf("Overall bpnzAC / total nonzero AC: %.9f%n", overallBpnzAC);
        System.out.printf("Bits per total coefficient: %.9f%n", bitsPerTotalCoefficient);
        System.out.println("Reference JPEG: " + ref.toAbsolutePath());
        System.out.println("Output stego JPEG: " + out.toAbsolutePath());

        /*
         * Complete evaluation block for thesis result collection.
         *
         * PNG vs Stego:
         *   includes JPEG compression + embedding distortion.
         *
         * Reference JPEG vs Stego:
         *   isolates steganographic embedding distortion.
         */
        EvaluationSummary eval =
                FullEvaluationAnalyzer.evaluate(
                        png.toString(),
                        ref.toString(),
                        out.toString(),
                        payloadBits,
                        selectedQF,
                        candidates.size(),
                        conservativeCapacity,
                        true,
                        true,
                        true
                );

        eval.print();

        Files.createDirectories(Path.of("build"));

        EvaluationCsvWriter.append(
                "build/evaluation_results.csv",
                png.getFileName().toString(),
                eval
        );

        System.out.println("Evaluation CSV updated: build/evaluation_results.csv");
    }

    private static void extract(Path png, Path stego, String password) throws Exception {
        byte[] header = null;
        int recoveredQF = -1;

        JpegImage stegoImg = JpegCore.readCoefficients(stego.toString());
        StcPipelineIntegration stc = new StcPipelineIntegration(STC_HEIGHT);

        for (int qf = MIN_QF; qf <= MAX_QF; qf++) {
            Path ref = Files.createTempFile("ahp-extract-qf" + qf + "-", ".jpg");

            try {
                ImageUtil.writeJpeg(ImageUtil.read(png), ref, qf);
                JpegImage refImg = JpegCore.readCoefficients(ref.toString());

                List<Candidate> candidates =
                        CandidateGenerator.generate(png, ref, refImg, HEADER_BITS);

                if (candidates.size() < HEADER_BITS) {
                    continue;
                }

                List<Candidate> headerCandidates =
                        candidates.subList(0, HEADER_BITS);

                byte[] stegoBits = coverBits(stegoImg, headerCandidates);
                byte[] trialHeader =
                        stc.extractPayload(stegoBits, PayloadPackage.headerBytes());

                if (PayloadPackage.validSync(trialHeader)) {
                    int qfInHeader = PayloadPackage.qfFromHeader(trialHeader);

                    if (qfInHeader == qf) {
                        header = trialHeader;
                        recoveredQF = qfInHeader;
                        break;
                    }
                }
            } finally {
                Files.deleteIfExists(ref);
            }
        }

        if (header == null) {
            throw new IllegalStateException(
                    "Could not recover valid payload header for any QF in range "
                            + MIN_QF + "-" + MAX_QF
            );
        }

        Path finalRef = Path.of("debug_ref_extract_qf" + recoveredQF + ".jpg");
        ImageUtil.writeJpeg(ImageUtil.read(png), finalRef, recoveredQF);

        JpegImage refImg = JpegCore.readCoefficients(finalRef.toString());

        int cipherLen = PayloadPackage.ciphertextLengthFromHeader(header);
        int totalBytes = PayloadPackage.headerBytes() + cipherLen;
        int totalBits = totalBytes * 8;

        List<Candidate> candidates =
                CandidateGenerator.generate(png, finalRef, refImg, totalBits);

        if (candidates.size() < HEADER_BITS + cipherLen * 8) {
            throw new IllegalStateException(
                    "Insufficient candidates for body extraction. required="
                            + (HEADER_BITS + cipherLen * 8)
                            + ", got="
                            + candidates.size()
            );
        }

        List<Candidate> bodyCandidates =
                candidates.subList(HEADER_BITS, HEADER_BITS + cipherLen * 8);

        byte[] bodyBits = coverBits(stegoImg, bodyCandidates);
        byte[] body = stc.extractPayload(bodyBits, cipherLen);

        byte[] packed = new byte[PayloadPackage.headerBytes() + cipherLen];

        System.arraycopy(
                header,
                0,
                packed,
                0,
                PayloadPackage.headerBytes()
        );

        System.arraycopy(
                body,
                0,
                packed,
                PayloadPackage.headerBytes(),
                cipherLen
        );

        byte[] plain = PayloadPackage.unpack(packed, password);

        System.out.println("Recovered QF: " + recoveredQF);
        System.out.println("Payload bytes recovered: " + packed.length);
        System.out.println("AES-GCM verified: true");
        System.out.println(new String(plain, StandardCharsets.UTF_8));
    }

    private static byte[] coverBits(JpegImage img, List<Candidate> candidates) {
        byte[] bits = new byte[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            int q = img.component(c.component).getCoeff(c.blockIndex, c.k);
            bits[i] = (byte) (Math.abs(q) & 1);
        }

        return bits;
    }

    private static double[] costs(List<Candidate> candidates) {
        double[] c = new double[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            c[i] = candidates.get(i).cost;
        }

        return c;
    }

    private static void applyParityChanges(
            JpegImage img,
            List<Candidate> candidates,
            byte[] coverBits,
            byte[] stegoBits
    ) {
        if (coverBits.length != stegoBits.length) {
            throw new IllegalStateException("STC returned invalid stego bit length.");
        }

        for (int i = 0; i < candidates.size(); i++) {
            if (coverBits[i] != stegoBits[i]) {
                Candidate c = candidates.get(i);
                JpegComponent comp = img.component(c.component);
                comp.setCoeff(c.blockIndex, c.k, c.bestParityFlipValue());
            }
        }
    }

    private static void makeReferenceJpeg(Path png, Path out, int qf) throws Exception {
        ImageUtil.writeJpeg(ImageUtil.read(png), out, qf);
        System.out.println("Reference JPEG written: " + out.toAbsolutePath());
        System.out.println("QF: " + qf);
    }

    private static void inspect(Path jpg) {
        JpegImage img = JpegCore.readCoefficients(jpg.toString());

        System.out.println(
                "width=" + img.width
                        + ", height=" + img.height
                        + ", comps=" + img.components.length
        );

        for (JpegComponent c : img.components) {
            System.out.println(
                    "component=" + c.componentIndex
                            + " blocks=" + c.widthBlocks + "x" + c.heightBlocks
                            + " coeffs=" + c.coefficients.length
            );
        }
    }

    private static void scanQfCapacity(Path png) throws Exception {
        System.out.println("========== ADAPTIVE QF CAPACITY SCAN ==========");

        for (int qf = MIN_QF; qf <= MAX_QF; qf++) {
            System.out.println("Probe QF=" + qf);

            Path ref = Files.createTempFile("ahp-scan-qf" + qf + "-", ".jpg");

            try {
                ImageUtil.writeJpeg(ImageUtil.read(png), ref, qf);
                JpegImage img = JpegCore.readCoefficients(ref.toString());

                long cbcr4 = countCbCr(img, true);
                long cbcr6 = countCbCr(img, false);

                System.out.printf(
                        "QF=%d | CbCr 4x4 nonzero=%d | conservative.STC=%d | CbCr 6x6 nonzero=%d | est=%d%n",
                        qf,
                        cbcr4,
                        Math.max(0, cbcr4 - 32),
                        cbcr6,
                        cbcr6
                );
            } finally {
                Files.deleteIfExists(ref);
            }
        }

        System.out.println("================================================");
    }

    private static long countCbCr(JpegImage img, boolean central4) {
        long total = 0;

        for (int c = 1; c <= 2 && c < img.components.length; c++) {
            JpegComponent comp = img.components[c];
            int blockCount = comp.widthBlocks * comp.heightBlocks;

            for (int b = 0; b < blockCount; b++) {
                int base = b * 64;

                for (int k = 0; k < 64; k++) {
                    int u = k % 8;
                    int v = k / 8;

                    if (u == 0 && v == 0) {
                        continue;
                    }

                    boolean ok = central4
                            ? (u >= 2 && u <= 5 && v >= 2 && v <= 5)
                            : (u >= 1 && u <= 6 && v >= 1 && v <= 6);

                    if (ok && comp.coefficients[base + k] != 0) {
                        total++;
                    }
                }
            }
        }

        return total;
    }

    private static long countTotalCoefficients(JpegImage img) {
        long total = 0;

        for (JpegComponent comp : img.components) {
            total += comp.coefficients.length;
        }

        return total;
    }

    private static long countTotalNonzeroAC(JpegImage img) {
        long total = 0;

        for (JpegComponent comp : img.components) {
            for (int i = 0; i < comp.coefficients.length; i++) {
                int k = i % 64;

                if (k != 0 && comp.coefficients[i] != 0) {
                    total++;
                }
            }
        }

        return total;
    }

    private static Map<String, String> parse(String[] a) {
        Map<String, String> m = new LinkedHashMap<>();

        for (int i = 0; i < a.length; i += 2) {
            if (i + 1 >= a.length) {
                throw new IllegalArgumentException("Missing value for " + a[i]);
            }

            m.put(a[i], a[i + 1]);
        }

        return m;
    }

    private static String req(Map<String, String> m, String k) {
        String v = m.get(k);

        if (v == null) {
            throw new IllegalArgumentException("Missing " + k);
        }

        return v;
    }

    private static void usage() {
        System.out.println("""
                Commands:
                embed --png input.png --msgFile msg.txt --out stego.jpg --password secret
                extract --png input.png --stego stego.jpg --password secret
                make-ref --png input.png --out reference.jpg --qf 85
                inspect --jpg image.jpg
                capacity --jpg image.jpg
                scan-qf --png input.png
                """);
    }
}
