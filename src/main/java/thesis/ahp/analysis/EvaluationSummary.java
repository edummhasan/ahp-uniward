package thesis.ahp.analysis;

public class EvaluationSummary {

    public ImageComparisonMetrics pngVsStego;
    public ImageComparisonMetrics referenceVsStego;
    public JpegCoefficientReport coefficientReport;

    public void print() {
        System.out.println();
        System.out.println("#########################################################");
        System.out.println("########## COMPLETE AHP-UNIWARD EVALUATION LOG ##########");
        System.out.println("#########################################################");

        if (pngVsStego != null) {
            pngVsStego.print();
        }

        if (referenceVsStego != null) {
            referenceVsStego.print();
        }

        if (coefficientReport != null) {
            coefficientReport.print();
        }

        printInterpretation();

        System.out.println("#########################################################");
        System.out.println();
    }

    private void printInterpretation() {
        if (coefficientReport == null) {
            return;
        }

        System.out.println("========== AUTOMATIC INTERPRETATION ==========");

        System.out.printf(
                "Adaptive QF selected: QF*=%d. This value was selected because it was the first feasible quality factor satisfying the payload-capacity constraint.%n",
                coefficientReport.selectedQF
        );

        if (coefficientReport.selectedQF >= 98) {
            System.out.println(
                    "QF realism warning: QF* is very high. This provides more capacity but may be less representative of typical real-world JPEG distributions and may be fragile under recompression."
            );
        } else if (coefficientReport.selectedQF <= 95) {
            System.out.println(
                    "QF realism: QF* is within a more common practical JPEG quality range."
            );
        }

        if (coefficientReport.bpnzAC < 0.2) {
            System.out.println("bpnzAC interpretation: low embedding density; generally safer.");
        } else if (coefficientReport.bpnzAC < 0.6) {
            System.out.println("bpnzAC interpretation: moderate embedding density.");
        } else {
            System.out.println("bpnzAC interpretation: high embedding density; detectability risk increases.");
        }

        if (referenceVsStego != null) {
            if (referenceVsStego.psnr >= 50.0) {
                System.out.println("PSNR interpretation: excellent visual fidelity.");
            } else if (referenceVsStego.psnr >= 40.0) {
                System.out.println("PSNR interpretation: high visual fidelity.");
            } else {
                System.out.println("PSNR interpretation: visible distortion may be possible.");
            }

            if (referenceVsStego.ssim >= 0.98) {
                System.out.println("SSIM interpretation: very high structural similarity.");
            } else if (referenceVsStego.ssim >= 0.95) {
                System.out.println("SSIM interpretation: acceptable structural similarity.");
            } else {
                System.out.println("SSIM interpretation: structural degradation may be significant.");
            }
        }

        System.out.println("Note: PSNR and SSIM measure visual quality only. They do not prove steganographic security. bpnzAC, histogram divergence, KL divergence, and detector-based steganalysis are needed for stronger security evaluation.");
        System.out.println("==============================================");
    }
}
