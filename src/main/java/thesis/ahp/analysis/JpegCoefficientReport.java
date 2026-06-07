package thesis.ahp.analysis;

public class JpegCoefficientReport {

    public int payloadBits;
    public int selectedQF;
    public int candidateCount;
    public int conservativeCapacity;

    public int totalCoefficients;
    public int totalNonzeroAC;

    public int modifiedCoefficients;
    public int modifiedY;
    public int modifiedCb;
    public int modifiedCr;

    public int modifiedNonzeroToNonzero;
    public int modifiedZeroToNonzero;
    public int modifiedNonzeroToZero;

    /**
     * Backward-compatible alias for candidateBpnzAC.
     * In the final report, prefer candidateBpnzAC for clarity.
     */
    public double bpnzAC;

    /**
     * Payload bits divided by the actual eligible candidate coefficients used by
     * the proposed Cb/Cr nonzero central-mask candidate rule.
     */
    public double candidateBpnzAC;

    /**
     * Payload bits divided by all nonzero AC coefficients in the JPEG image.
     * This is the broader JPEG-domain bpnzAC used for literature comparison.
     */
    public double overallBpnzAC;

    /**
     * Payload bits divided by all DCT coefficients, including DC, AC, zero,
     * and nonzero coefficients.
     */
    public double bitsPerTotalCoefficient;

    public double modificationRateAllCoefficients;
    public double modificationRateCandidates;

    public double dctHistogramL1;
    public double dctKLDivergence;

    public boolean embeddingSuccess;
    public boolean extractionSuccess;
    public boolean aesGcmVerified;

    public void print() {
        System.out.println("========== JPEG COEFFICIENT / EMBEDDING REPORT ==========");
        System.out.printf("Selected QF*: %d%n", selectedQF);
        System.out.printf("Payload bits: %d%n", payloadBits);
        System.out.printf("Candidate count: %d%n", candidateCount);
        System.out.printf("Conservative STC capacity: %d%n", conservativeCapacity);
        System.out.printf("Total coefficients: %d%n", totalCoefficients);
        System.out.printf("Total nonzero AC coefficients: %d%n", totalNonzeroAC);
        System.out.printf("Candidate-normalized bpnzAC: %.6f%n", candidateBpnzAC);
        System.out.printf("Overall bpnzAC / total nonzero AC: %.9f%n", overallBpnzAC);
        System.out.printf("Bits per total coefficient: %.9f%n", bitsPerTotalCoefficient);
        System.out.printf("Modified coefficients: %d%n", modifiedCoefficients);
        System.out.printf("Modified Y: %d%n", modifiedY);
        System.out.printf("Modified Cb: %d%n", modifiedCb);
        System.out.printf("Modified Cr: %d%n", modifiedCr);
        System.out.printf("Nonzero -> Nonzero: %d%n", modifiedNonzeroToNonzero);
        System.out.printf("Zero -> Nonzero: %d%n", modifiedZeroToNonzero);
        System.out.printf("Nonzero -> Zero: %d%n", modifiedNonzeroToZero);
        System.out.printf("Modification rate / all coefficients: %.9f%n", modificationRateAllCoefficients);
        System.out.printf("Modification rate / candidates: %.9f%n", modificationRateCandidates);
        System.out.printf("DCT Histogram L1 Divergence: %.9f%n", dctHistogramL1);
        System.out.printf("DCT KL Divergence: %.9f%n", dctKLDivergence);
        System.out.printf("Embedding success: %s%n", embeddingSuccess);
        System.out.printf("Extraction success: %s%n", extractionSuccess);
        System.out.printf("AES-GCM verified: %s%n", aesGcmVerified);
        System.out.println("=========================================================");
    }
}
