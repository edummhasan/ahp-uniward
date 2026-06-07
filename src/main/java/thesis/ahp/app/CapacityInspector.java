package thesis.ahp.app;

import thesis.ahp.jni.JpegComponent;
import thesis.ahp.jni.JpegCore;
import thesis.ahp.jni.JpegImage;

public final class CapacityInspector {

    private CapacityInspector() {}

    public static void inspect(String jpegPath) {
        JpegImage img = JpegCore.readCoefficients(jpegPath);

        System.out.println("========== IMAGE CAPACITY REPORT ==========");
        System.out.println("Image: " + jpegPath);
        System.out.println("Embedding channels: Cb and Cr only");
        System.out.println("Total components: " + img.components.length);
        System.out.println();

        long total4Nonzero = 0;
        long total6Nonzero = 0;
        long total6IncludingZero = 0;

        for (int c = 0; c < img.components.length; c++) {
            JpegComponent comp = img.components[c];

            String name = switch (c) {
                case 0 -> "Y";
                case 1 -> "Cb";
                case 2 -> "Cr";
                default -> "Component-" + c;
            };

            int blockCount = comp.widthBlocks * comp.heightBlocks;

            long central4Nonzero = 0;
            long central6Nonzero = 0;
            long central6IncludingZero = 0;

            for (int b = 0; b < blockCount; b++) {
                int base = b * 64;

                for (int k = 0; k < 64; k++) {
                    int u = k % 8;
                    int v = k / 8;
                    int coeff = comp.coefficients[base + k];

                    if (u == 0 && v == 0) {
                        continue;
                    }

                    boolean in4 = isCentral4x4(u, v);
                    boolean in6 = isCentral6x6(u, v);

                    if (in4 && coeff != 0) {
                        central4Nonzero++;
                    }

                    if (in6 && coeff != 0) {
                        central6Nonzero++;
                    }

                    if (in6) {
                        central6IncludingZero++;
                    }
                }
            }

            System.out.println(name + " component:");
            System.out.println("  blocks: " + blockCount);
            System.out.println("  central 4x4 nonzero candidates: " + central4Nonzero);
            System.out.println("  central 6x6 nonzero candidates: " + central6Nonzero);
            System.out.println("  central 6x6 including-zero candidates: " + central6IncludingZero);
            System.out.println();

            if (c == 1 || c == 2) {
                total4Nonzero += central4Nonzero;
                total6Nonzero += central6Nonzero;
                total6IncludingZero += central6IncludingZero;
            }
        }

        System.out.println("TOTAL Cb/Cr capacity:");
        System.out.println("  4x4 nonzero: " + total4Nonzero + " bits");
        System.out.println("  6x6 nonzero: " + total6Nonzero + " bits");
        System.out.println("  6x6 including zero: " + total6IncludingZero + " bits");
        System.out.println();

        System.out.println("Estimated STC usable payload:");
        System.out.println("  4x4 nonzero @ 0.5 rate: " + (total4Nonzero / 2) + " bits");
        System.out.println("  6x6 nonzero @ 0.5 rate: " + (total6Nonzero / 2) + " bits");
        System.out.println("  6x6 including zero @ 0.5 rate: " + (total6IncludingZero / 2) + " bits");
        System.out.println("===========================================");
    }

    private static boolean isCentral4x4(int u, int v) {
        return u >= 2 && u <= 5 && v >= 2 && v <= 5;
    }

    private static boolean isCentral6x6(int u, int v) {
        return u >= 1 && u <= 6 && v >= 1 && v <= 6;
    }
}
