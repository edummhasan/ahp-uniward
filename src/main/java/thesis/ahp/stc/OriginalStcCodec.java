package thesis.ahp.stc;

import java.util.Objects;

/** Java wrapper for the official/native STC implementation. */
public final class OriginalStcCodec implements StcCodec {

    @Override
    public byte[] embed(byte[] coverBits, double[] costs, byte[] messageBits, int constraintHeight) {
        validateBits(coverBits, "coverBits");
        validateBits(messageBits, "messageBits");
        Objects.requireNonNull(costs, "costs");

        if (coverBits.length != costs.length) {
            throw new IllegalArgumentException("coverBits and costs must have the same length.");
        }
        if (messageBits.length == 0) {
            throw new IllegalArgumentException("messageBits cannot be empty.");
        }
        if (constraintHeight < 4 || constraintHeight > 20) {
            throw new IllegalArgumentException("constraintHeight should normally be between 4 and 20.");
        }

        return OriginalStcNative.embedBinary(coverBits, costs, messageBits, constraintHeight);
    }

    @Override
    public byte[] extract(byte[] stegoBits, int messageBitLength, int constraintHeight) {
        validateBits(stegoBits, "stegoBits");

        if (messageBitLength <= 0) {
            throw new IllegalArgumentException("messageBitLength must be positive.");
        }
        if (constraintHeight < 4 || constraintHeight > 20) {
            throw new IllegalArgumentException("constraintHeight should normally be between 4 and 20.");
        }

        return OriginalStcNative.extractBinary(stegoBits, messageBitLength, constraintHeight);
    }

    private static void validateBits(byte[] bits, String name) {
        Objects.requireNonNull(bits, name);
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] != 0 && bits[i] != 1) {
                throw new IllegalArgumentException(name + " contains non-binary value at index " + i);
            }
        }
    }
}
