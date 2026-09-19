package java.awt;
import java.awt.geom.Rectangle2D;
public class Rectangle extends Rectangle2D {
    public int x;
    public int y;
    public int width;
    public int height;
    public Rectangle() {}
    public Rectangle(Rectangle r) { this(r.x, r.y, r.width, r.height); }
    public Rectangle(int width, int height) { this(0, 0, width, height); }
    public Rectangle(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    public Rectangle(Rectangle2D r) { this((int) r.getX(), (int) r.getY(), (int) r.getWidth(), (int) r.getHeight()); }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isEmpty() { return width <= 0 || height <= 0; }
    public Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    public Rectangle2D getBounds2D() { return new Rectangle2D.Double(x, y, width, height); }
    public void setRect(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    public void setRect(double x, double y, double width, double height) { setRect((int) x, (int) y, (int) width, (int) height); }
    public void setFrame(double x, double y, double w, double h) { setRect(x, y, w, h); }
    public void add(Rectangle r) {
        int x1 = Math.min(x, r.x);
        int y1 = Math.min(y, r.y);
        int x2 = Math.max(x + width, r.x + r.width);
        int y2 = Math.max(y + height, r.y + r.height);
        setRect(x1, y1, x2 - x1, y2 - y1);
    }
    public void translate(int dx, int dy) { x += dx; y += dy; }
    public boolean contains(int px, int py) { return px >= x && px < x + width && py >= y && py < y + height; }
    public boolean contains(Point p) { return contains(p.x, p.y); }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Rectangle)) return false;
        Rectangle r = (Rectangle) other;
        return x == r.x && y == r.y && width == r.width && height == r.height;
    }
    public int hashCode() { return x * 31 + y; }
    public String toString() { return "Rectangle[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]"; }
}