package thesis.ahp.analysis;

import thesis.ahp.jni.JpegCore;
import thesis.ahp.jni.JpegImage;

public final class FullEvaluationAnalyzer {

    private FullEvaluationAnalyzer() {}

    public static EvaluationSummary evaluate(
            String originalPngPath,
            String referenceJpegPath,
            String stegoJpegPath,
            int payloadBits,
            int selectedQF,
            int candidateCount,
            int conservativeCapacity,
            boolean embeddingSuccess,
            boolean extractionSuccess,
            boolean aesGcmVerified
    ) throws Exception {

        EvaluationSummary summary = new EvaluationSummary();

        summary.pngVsStego =
                ImageQualityAnalyzer.compare(
                        "Original PNG vs Stego JPEG",
                        originalPngPath,
                        stegoJpegPath
                );

        summary.referenceVsStego =
                ImageQualityAnalyzer.compare(
                        "Reference JPEG vs Stego JPEG",
                        referenceJpegPath,
                        stegoJpegPath
                );

        JpegImage refJpeg = JpegCore.readCoefficients(referenceJpegPath);
        JpegImage stegoJpeg = JpegCore.readCoefficients(stegoJpegPath);

        summary.coefficientReport =
                JpegCoefficientAnalyzer.compare(
                        refJpeg,
                        stegoJpeg,
                        payloadBits,
                        selectedQF,
                        candidateCount,
                        conservativeCapacity,
                        embeddingSuccess,
                        extractionSuccess,
                        aesGcmVerified
                );

        return summary;
    }
}
