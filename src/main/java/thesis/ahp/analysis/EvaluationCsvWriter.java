package thesis.ahp.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class EvaluationCsvWriter {

    private EvaluationCsvWriter() {}

    public static void append(String csvPath, String imageName, EvaluationSummary s) throws IOException {
        File f = new File(csvPath);
        boolean writeHeader = !f.exists();

        try (FileWriter fw = new FileWriter(f, true)) {
            if (writeHeader) {
                fw.write(String.join(",",
                        "image",
                        "qf",
                        "payload_bits",
                        "candidate_count",
                        "stc_capacity",
                        "bpnzAC",
                        "modified_coefficients",
                        "modified_Y",
                        "modified_Cb",
                        "modified_Cr",
                        "modification_rate_candidates",
                        "ref_stego_mse",
                        "ref_stego_psnr",
                        "ref_stego_ssim",
                        "ref_stego_entropy_diff",
                        "ref_stego_hist_l1",
                        "ref_stego_kl",
                        "dct_hist_l1",
                        "dct_kl",
                        "embedding_success",
                        "extraction_success",
                        "aes_gcm_verified"
                ));
                fw.write("\n");
            }

            JpegCoefficientReport c = s.coefficientReport;
            ImageComparisonMetrics r = s.referenceVsStego;

            fw.write(String.join(",",
                    safe(imageName),
                    String.valueOf(c.selectedQF),
                    String.valueOf(c.payloadBits),
                    String.valueOf(c.candidateCount),
                    String.valueOf(c.conservativeCapacity),
                    fmt(c.bpnzAC),
                    String.valueOf(c.modifiedCoefficients),
                    String.valueOf(c.modifiedY),
                    String.valueOf(c.modifiedCb),
                    String.valueOf(c.modifiedCr),
                    fmt(c.modificationRateCandidates),
                    fmt(r.mse),
                    fmt(r.psnr),
                    fmt(r.ssim),
                    fmt(r.entropyDiff),
                    fmt(r.histogramL1),
                    fmt(r.klDivergence),
                    fmt(c.dctHistogramL1),
                    fmt(c.dctKLDivergence),
                    String.valueOf(c.embeddingSuccess),
                    String.valueOf(c.extractionSuccess),
                    String.valueOf(c.aesGcmVerified)
            ));
            fw.write("\n");
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace(",", "_");
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.9f", v);
    }
}
