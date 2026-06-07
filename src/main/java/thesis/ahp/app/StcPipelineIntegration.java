package thesis.ahp.app;

import thesis.ahp.stc.BitPacking;
import thesis.ahp.stc.OriginalStcCodec;
import thesis.ahp.stc.StcCodec;

public final class StcPipelineIntegration {
    private final StcCodec codec;
    private final int constraintHeight;

    public StcPipelineIntegration(int constraintHeight) {
        this.codec = new OriginalStcCodec();
        this.constraintHeight = constraintHeight;
    }

    public byte[] embedPayload(byte[] coverBits, double[] costs, byte[] payloadBytes) {
        byte[] messageBits = BitPacking.bytesToBits(payloadBytes);
        return codec.embed(coverBits, costs, messageBits, constraintHeight);
    }

    public byte[] extractPayload(byte[] stegoBits, int payloadByteLength) {
        int messageBitLength = payloadByteLength * 8;
        byte[] recoveredBits = codec.extract(stegoBits, messageBitLength, constraintHeight);
        return BitPacking.bitsToBytes(recoveredBits);
    }
}
