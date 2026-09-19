package java.awt.image;

import java.awt.Graphics2D;

/**
 * 最小桩：DrawPaint 的纹理填充路径引用本类。
 *
 * 只实现最基本的读写，够 POI 完成类链接；应用内合成图片走 Bitmap/Canvas。
 */
public class BufferedImage {
    public static final int TYPE_INT_ARGB = 2;
    public static final int TYPE_INT_RGB = 1;

    private final int width;
    private final int height;
    private final int[] pixels;

    public BufferedImage(int width, int height, int imageType) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.pixels = new int[this.width * this.height];
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public int getRGB(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return pixels[y * width + x];
    }

    public void setRGB(int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        pixels[y * width + x] = rgb;
    }

    public int[] getRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
        int[] out = rgbArray != null ? rgbArray : new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                out[offset + yy * scansize + xx] = getRGB(startX + xx, startY + yy);
            }
        }
        return out;
    }

    public Graphics2D createGraphics() { return new Graphics2D(); }
}
