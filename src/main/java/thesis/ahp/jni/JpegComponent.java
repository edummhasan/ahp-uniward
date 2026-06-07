package thesis.ahp.jni;

public class JpegComponent {
    public final int componentIndex;
    public final int widthBlocks;
    public final int heightBlocks;
    public final int[] coefficients; // blocks * 64 in JPEG natural order
    public final int[] quantTable;    // 64 entries

    public JpegComponent(int componentIndex, int widthBlocks, int heightBlocks, int[] coefficients, int[] quantTable) {
        this.componentIndex = componentIndex;
        this.widthBlocks = widthBlocks;
        this.heightBlocks = heightBlocks;
        this.coefficients = coefficients;
        this.quantTable = quantTable;
    }

    public int blockCount() { return widthBlocks * heightBlocks; }
    public int getCoeff(int blockIndex, int k) { return coefficients[blockIndex * 64 + k]; }
    public void setCoeff(int blockIndex, int k, int v) { coefficients[blockIndex * 64 + k] = v; }
}
