package thesis.ahp.stc;

public final class OriginalStcNative {
    static {
        System.loadLibrary("originalstcjni");
    }

    private OriginalStcNative() {}

    public static native byte[] embedBinary(
            byte[] coverBits,
            double[] costs,
            byte[] messageBits,
            int constraintHeight
    );

    public static native byte[] extractBinary(
            byte[] stegoBits,
            int messageBitLength,
            int constraintHeight
    );
}
