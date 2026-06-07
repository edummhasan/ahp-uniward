package thesis.ahp.image;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.Iterator;

public final class ImageUtil {
    private ImageUtil() {}
    public static BufferedImage read(Path p) throws IOException {
        BufferedImage img = ImageIO.read(p.toFile());
        if (img == null) throw new IOException("Cannot read image: " + p);
        return toRGB(img);
    }
    public static BufferedImage toRGB(BufferedImage in) {
        if (in.getType() == BufferedImage.TYPE_INT_RGB) return in;
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,out.getWidth(),out.getHeight()); g.drawImage(in,0,0,null); g.dispose();
        return out;
    }
    public static void writeJpeg(BufferedImage img, Path out, int qf) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.max(0f, Math.min(1f, qf / 100f)));
        try (OutputStream os = new FileOutputStream(out.toFile()); ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios); writer.write(null, new IIOImage(toRGB(img), null, null), param);
        } finally { writer.dispose(); }
    }
    public static double[][][] yCbCr(BufferedImage img) {
        img = toRGB(img); int w=img.getWidth(), h=img.getHeight();
        double[][] Y=new double[h][w], Cb=new double[h][w], Cr=new double[h][w];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){
            int rgb=img.getRGB(x,y); int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
            Y[y][x]=0.299*r+0.587*g+0.114*b;
            Cb[y][x]=128-0.168736*r-0.331264*g+0.5*b;
            Cr[y][x]=128+0.5*r-0.418688*g-0.081312*b;
        }
        return new double[][][]{Y,Cb,Cr};
    }
    public static double[] histogram(double[][] plane) {
        double[] h=new double[256]; int n=plane.length*plane[0].length;
        for(double[] row: plane) for(double v: row) h[clamp((int)Math.round(v))]++;
        for(int i=0;i<256;i++) h[i]/=n; return h;
    }
    public static int clamp(int v){ return Math.max(0, Math.min(255,v)); }
}
