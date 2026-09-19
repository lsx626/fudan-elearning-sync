package java.awt;
public class Color {
    int value;
    public Color(int rgb) { this.value = rgb & 0xFFFFFFFF; }
    public Color(int r, int g, int b) { this(r, g, b, 255); }
    public Color(int r, int g, int b, int a) {
        value = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    public int getRGB() { return value; }
    public int getRed() { return (value >> 16) & 0xFF; }
    public int getGreen() { return (value >> 8) & 0xFF; }
    public int getBlue() { return value & 0xFF; }
    public int getAlpha() { return (value >> 24) & 0xFF; }
    public Color brighter() { return new Color(Math.min(255, getRed() + 32), Math.min(255, getGreen() + 32), Math.min(255, getBlue() + 32)); }
    public Color darker() { return new Color(Math.max(0, getRed() - 32), Math.max(0, getGreen() - 32), Math.max(0, getBlue() - 32)); }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Color)) return false;
        return value == ((Color) other).value;
    }
    public int hashCode() { return value; }
    public String toString() { return "Color[r=" + getRed() + ",g=" + getGreen() + ",b=" + getBlue() + "]"; }
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color RED = new Color(255, 0, 0);
    public static final Color GREEN = new Color(0, 255, 0);
    public static final Color BLUE = new Color(0, 0, 255);
}