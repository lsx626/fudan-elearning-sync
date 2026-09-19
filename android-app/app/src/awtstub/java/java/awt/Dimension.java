package java.awt;
public class Dimension {
    public int width;
    public int height;
    public Dimension() {}
    public Dimension(int width, int height) { this.width = width; this.height = height; }
    public Dimension(Dimension d) { this(d.width, d.height); }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public void setSize(double width, double height) { this.width = (int) width; this.height = (int) height; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public void setSize(Dimension d) { setSize(d.width, d.height); }
    public Dimension getSize() { return new Dimension(width, height); }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Dimension)) return false;
        Dimension d = (Dimension) other;
        return width == d.width && height == d.height;
    }
    public int hashCode() { return width * 31 + height; }
    public String toString() { return "Dimension[width=" + width + ",height=" + height + "]"; }
}