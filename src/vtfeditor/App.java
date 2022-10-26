package vtfeditor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.awt.Graphics;

public class App {
    private static final String[] formats = {
        "RGBA8888",
        "ABGR8888",
        "RGB888",
        "BGR888",
        "RGB565",
        "I8",
        "IA88",
        "P8",
        "A8",
        "RGB888_BL",
        "BGR888_BL",
        "ARGB8888",
        "BGRA8888",
        "DXT1",
        "DXT3",
        "DXT5",
        "BGRX8888",
        "BGR565",
        "BGRX5551",
        "BGRA4444",
        "DXT1_ONEBITALPHA",
        "BGRA5551",
        "UV88",
        "UVWQ8888",
        "RGBA16161616F",
        "RGBA16161616",
        "UVLX8888",
    };
    private static byte[] data;
    private static String signature;
    private static int version;
    private static int subVersion;
    private static int headerSize;
    private static int width;
    private static int height;
    private static int flags;
    private static int frames;
    private static int firstFrame;
    private static int[] padding0 = new int[4];
    private static float[] reflectivity = new float[3];
    private static int[] padding1 = new int[4];
    private static float bumpmapScale;
    private static int highResImageFormat;
    private static int mipmapCount;
    private static int lowResImageFormat;
    private static int lowResImageWidth;
    private static int lowResImageHeight;
    private static int depth;
    private static int[] rgb565(int c)
    {
        int r = (c & 0xf800) >>> 8;
        int g = (c & 0x07e0) >>> 3;
        int b = (c & 0x001f) << 3;
        r |= r >>> 5;
        g |= g >>> 6;
        b |= b >>> 5;
        return new int[] {r, g, b};
    }
    private static int argb8888(int a, int r, int g, int b)
    {
        return a << 24 | r << 16 | g << 8 | b;
    }
    private static void decompressDXT5block(byte[] block, BufferedImage image, int s, int t){
        int[] buffer = new int[16];
        int[] alpha = new int[8];
        alpha[0] = block[0] & 0xFF;
        alpha[1] = block[1] & 0xFF;
        if (alpha[0] > alpha[1])
        {
            alpha[2] = (6 * alpha[0] + 1 * alpha[1]) / 7;
            alpha[3] = (5 * alpha[0] + 2 * alpha[1]) / 7;
            alpha[4] = (4 * alpha[0] + 3 * alpha[1]) / 7;
            alpha[5] = (3 * alpha[0] + 4 * alpha[1]) / 7;
            alpha[6] = (2 * alpha[0] + 5 * alpha[1]) / 7;
            alpha[7] = (1 * alpha[0] + 6 * alpha[1]) / 7;
        }
        else
        {
            alpha[2] = (4 * alpha[0] + 1 * alpha[1]) / 5;
            alpha[3] = (3 * alpha[0] + 2 * alpha[1]) / 5;
            alpha[4] = (2 * alpha[0] + 3 * alpha[1]) / 5;
            alpha[5] = (1 * alpha[0] + 4 * alpha[1]) / 5;
            alpha[6] = 0;
            alpha[7] = 255;
        }

        int d = 0;
        d |= block[2] & 0xFF;
        d |= (block[3] & 0xFF) << 8;
        d |= (block[4] & 0xFF) << 16;
        d |= (block[5] & 0xFF) << 24;
        d |= (block[6] & 0xFF) << 32;
        d |= (block[7] & 0xFF) << 40;
        for (int i = 0; i < 16; i++, d >>>= 3)
        {
            buffer[i] = alpha[d & 7];
        }

        for (int y = 0; y < 4; y++)
        {
            for (int x = 0; x < 4; x++)
            {
                int px = s * 4 + x;
                int py = t * 4 + y;
                int argb = image.getRGB(px,py);
                argb &= 0x00FFFFFF;
                argb += buffer[y * 4 + x] << 24;
                image.setRGB(px, py, argb);
            }
        }
    }
    private static void decompressDXT1block(byte[] block, BufferedImage image, int s, int t){
        int[] buffer = new int[16];
        int[] colors = new int[4];
        int r0, g0, b0, r1, g1, b1;
        int q0 = (block[0] & 0xFF) | ((block[1] & 0xFF) << 8);
        int q1 = (block[2] & 0xFF) | ((block[3] & 0xFF) << 8);
        int[] rgb0 = rgb565(q0);
        r0 = rgb0[0];
        g0 = rgb0[1];
        b0 = rgb0[2];
        int[] rgb1 = rgb565(q1);
        r1 = rgb1[0];
        g1 = rgb1[1];
        b1 = rgb1[2];
        colors[0] = argb8888(255, r0, g0, b0);
        colors[1] = argb8888(255, r1, g1, b1);
        colors[2] = argb8888(255, (r0 * 2 + r1) / 3, (g0 * 2 + g1) / 3, (b0 * 2 + b1) / 3);
        colors[3] = argb8888(255, (r0 + r1 * 2) / 3, (g0 + g1 * 2) / 3, (b0 + b1 * 2) / 3);

        int d = 0;
        d |= block[4] & 0xFF;
        d |= (block[5] & 0xFF) << 8;
        d |= (block[6] & 0xFF) << 16;
        d |= (block[7] & 0xFF) << 24;
        for (int i = 0; i < 16; i++, d >>>= 2)
        {
            buffer[i] = colors[d & 3];
        }

        for (int y = 0; y < 4; y++)
        {
            for (int x = 0; x < 4; x++)
            {
                int px = s * 4 + x;
                int py = t * 4 + y;
                image.setRGB(px, py, buffer[y * 4 + x]);
            }
        }
    }
    private static void decompressDXT1(byte[] input, int width, int height, BufferedImage image){
        int offset = 0;
        int bcw = width / 4;
        int bch = height / 4;
        
        for (int t = 0; t < bch; t++)
        {
            for (int s = 0; s < bcw; s++, offset += 8)
            {
                byte[] block = Arrays.copyOfRange(input, offset, offset + 8);
                decompressDXT1block(block, image, s, t);
            }
        }
    }
    private static void decompressDXT5(byte[] input, int width, int height, BufferedImage image){
        int offset = 0;
        int bcw = width / 4;
        int bch = height / 4;
        
        for (int t = 0; t < bch; t++)
        {
            for (int s = 0; s < bcw; s++, offset += 16)
            {
                byte[] block = Arrays.copyOfRange(input, offset, offset + 16);
                byte[] alphab = Arrays.copyOfRange(block, 0, 8);
                byte[] dxt1b = Arrays.copyOfRange(block, 8, 16);
                decompressDXT1block(dxt1b, image, s, t);
                decompressDXT5block(alphab, image, s, t);
            }
        }
    }
    private static int DXT5MipmapSize(int w, int h){
        if(w <= 4 && h <= 4){
            return 16;
        }
        return w * h / 16 * 128 / 8;
    }
    private static int DXT1MipmapSize(int w, int h){
        if(w <= 4 && h <= 4){
            return 8;
        }
        return w * h / 16 * 64 / 8;
    }
    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //fit the screen size
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        if(args.length < 1){
            System.out.println("no input file");
            //open file explorer
            JFileChooser chooser = new JFileChooser();
            chooser.showOpenDialog(frame);
            File file = chooser.getSelectedFile();
            if(file == null){
                System.exit(0);
            }
            args = new String[]{file.getAbsolutePath()};
        }
        if(args[0].endsWith("vtf")){
            data = Files.readAllBytes(Paths.get(args[0]));
            signature = new String(data, 0, 4);
            version = (data[4] & 0xFF) | ((data[5] & 0xFF) << 8) | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 24);
            subVersion = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8) | ((data[10] & 0xFF) << 16) | ((data[11] & 0xFF) << 24);
            headerSize = (data[12] & 0xFF) | ((data[13] & 0xFF) << 8) | ((data[14] & 0xFF) << 16) | ((data[15] & 0xFF) << 24);
            width = (data[16] & 0xFF) | ((data[17] & 0xFF) << 8);
            height = (data[18] & 0xFF) | ((data[19] & 0xFF) << 8);
            flags = (data[20] & 0xFF) | ((data[21] & 0xFF) << 8) | ((data[22] & 0xFF) << 16) | ((data[23] & 0xFF) << 24);
            frames = (data[24] & 0xFF) | ((data[25] & 0xFF) << 8);
            firstFrame = (data[26] & 0xFF) | ((data[27] & 0xFF) << 8);
            padding0[0] = data[28] & 0xff;
            padding0[1] = data[29] & 0xff;
            padding0[2] = data[30] & 0xff;
            padding0[3] = data[31] & 0xff;
            reflectivity[0] = (data[32] & 0xFF) | ((data[33] & 0xFF) << 8) | ((data[34] & 0xFF) << 16) | ((data[35] & 0xFF) << 24);
            reflectivity[1] = (data[36] & 0xFF) | ((data[37] & 0xFF) << 8) | ((data[38] & 0xFF) << 16) | ((data[39] & 0xFF) << 24);
            reflectivity[2] = (data[40] & 0xFF) | ((data[41] & 0xFF) << 8) | ((data[42] & 0xFF) << 16) | ((data[43] & 0xFF) << 24);
            padding1[0] = data[44] & 0xff;
            padding1[1] = data[45] & 0xff;
            padding1[2] = data[46] & 0xff;
            padding1[3] = data[47] & 0xff;
            bumpmapScale = (data[48] & 0xFF) | ((data[49] & 0xFF) << 8) | ((data[50] & 0xFF) << 16) | ((data[51] & 0xFF) << 24);
            highResImageFormat = (data[52] & 0xFF) | ((data[53] & 0xFF) << 8) | ((data[54] & 0xFF) << 16) | ((data[55] & 0xFF) << 24);
            mipmapCount = (data[56] & 0xFF);
            lowResImageFormat = (data[57] & 0xFF) | ((data[58] & 0xFF) << 8) | ((data[59] & 0xFF) << 16) | ((data[60] & 0xFF) << 24);
            lowResImageWidth = (data[61] & 0xFF);
            lowResImageHeight = (data[62] & 0xFF);
            depth = (data[63] & 0xFF);
            System.out.println("signature " + signature);
            System.out.println("version " + version + "." + subVersion);
            System.out.println("headerSize " + headerSize);
            System.out.println("width " + width);
            System.out.println("height " + height);
            System.out.println("flags " + Integer.toHexString(flags));
            System.out.println("frames " + frames);
            System.out.println("firstFrame " + firstFrame);
            System.out.println("padding0 " + padding0[0] + " " + padding0[1] + " " + padding0[2] + " " + padding0[3]);
            System.out.println("reflectivity " + reflectivity[0] + " " + reflectivity[1] + " " + reflectivity[2]);
            System.out.println("padding1 " + padding1[0] + " " + padding1[1] + " " + padding1[2] + " " + padding1[3]);
            System.out.println("bumpmapScale " + bumpmapScale);
            System.out.println("highResImageFormat " + formats[highResImageFormat]);
            System.out.println("mipmapCount " + mipmapCount);
            System.out.println("lowResImageFormat " + formats[lowResImageFormat]);
            System.out.println("lowResImageWidth " + lowResImageWidth);
            System.out.println("lowResImageHeight " + lowResImageHeight);
            System.out.println("depth " + depth);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.drawImage(image, 0, 0, null);
                }
            };
            panel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            frame.getContentPane().add(panel);
            frame.getContentPane().add(new JLabel(new ImageIcon(image)));
            //add horizontal and vertical scrollbars if needed
            frame.getContentPane().add(new JScrollPane(panel), BorderLayout.CENTER);
            frame.setVisible(true);
            int offset = headerSize + DXT1MipmapSize(lowResImageWidth, lowResImageHeight);
            if(highResImageFormat == 13){
                for(int i=0;i<mipmapCount-1;i++){
                    int w = (int) Math.pow(2, i);
                    int h = (int) Math.pow(2, i);
                    offset += DXT1MipmapSize(w, h);
                }
                byte[] input = Arrays.copyOfRange(data, offset, data.length);
                decompressDXT1(input, width, height, image);
            }
            else{
                for(int i=0;i<mipmapCount-1;i++){
                    int w = (int) Math.pow(2, i);
                    int h = (int) Math.pow(2, i);
                    offset += DXT5MipmapSize(w, h);
                }
                byte[] input = Arrays.copyOfRange(data, offset, data.length);
                decompressDXT5(input, width, height, image);
            }
            panel.repaint();
        }
    }
}
