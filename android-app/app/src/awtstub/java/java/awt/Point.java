package java.awt;
import java.awt.geom.Point2D;
public class Point extends Point2D {
    public int x;
    public int y;
    public Point() {}
    public Point(int x, int y) { this.x = x; this.y = y; }
    public Point(Point p) { this(p.x, p.y); }
    public Point(double x, double y) { this.x = (int) x; this.y = (int) y; }
    public Point(Point2D p) { this(p.getX(), p.getY()); }
    public double getX() { return x; }
    public double getY() { return y; }
    public Point getLocation() { return new Point(x, y); }
    public void setLocation(int x, int y) { this.x = x; this.y = y; }
    public void setLocation(Point p) { setLocation(p.x, p.y); }
    public void setLocation(double x, double y) { this.x = (int) x; this.y = (int) y; }
    public void move(int x, int y) { this.x = x; this.y = y; }
    public void translate(int dx, int dy) { this.x += dx; this.y += dy; }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Point)) return false;
        Point p = (Point) other;
        return x == p.x && y == p.y;
    }
    public int hashCode() { return x * 31 + y; }
    public String toString() { return "Point[x=" + x + ",y=" + y + "]"; }
}