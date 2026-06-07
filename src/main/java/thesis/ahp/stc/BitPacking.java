package thesis.ahp.stc;

public final class BitPacking {
    private BitPacking() {}

    public static byte[] bytesToBits(byte[] bytes) {
        byte[] bits = new byte[bytes.length * 8];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = (byte) ((bytes[i / 8] >> (7 - (i % 8))) & 1);
        }
        return bits;
    }

    public static byte[] bitsToBytes(byte[] bits) {
        int n = (bits.length + 7) / 8;
        byte[] out = new byte[n];
        for (int i = 0; i < bits.length; i++) {
            out[i / 8] |= (byte) ((bits[i] & 1) << (7 - (i % 8)));
        }
        return out;
    }
}
