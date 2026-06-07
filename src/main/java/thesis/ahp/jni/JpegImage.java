package thesis.ahp.jni;

public class JpegImage {
    public final int width;
    public final int height;
    public final JpegComponent[] components;

    public JpegImage(int width, int height, JpegComponent[] components) {
        this.width = width;
        this.height = height;
        this.components = components;
    }

    public JpegComponent component(int index) { return components[index]; }
}
