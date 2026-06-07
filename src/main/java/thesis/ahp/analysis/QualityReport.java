package thesis.ahp.analysis;

public class QualityReport {

    public double psnr;
    public double mse;
    public double ssim;

    /** Backward-compatible alias for candidateBpnzAC. */
    public double bpnzAC;

    public double candidateBpnzAC;
    public double overallBpnzAC;
    public double bitsPerTotalCoefficient;

    public int payloadBits;
    public int qf;

    public void print() {
        System.out.println("========== QUALITY REPORT ==========");
        System.out.printf("QF: %d%n", qf);
        System.out.printf("Payload bits: %d%n", payloadBits);
        System.out.printf("Candidate-normalized bpnzAC: %.6f%n", candidateBpnzAC);

        if (overallBpnzAC > 0.0) {
            System.out.printf("Overall bpnzAC / total nonzero AC: %.9f%n", overallBpnzAC);
        }

        if (bitsPerTotalCoefficient > 0.0) {
            System.out.printf("Bits per total coefficient: %.9f%n", bitsPerTotalCoefficient);
        }

        System.out.printf("MSE: %.4f%n", mse);
        System.out.printf("PSNR: %.4f dB%n", psnr);
        System.out.printf("SSIM: %.4f%n", ssim);
        System.out.println("====================================");
    }
}
