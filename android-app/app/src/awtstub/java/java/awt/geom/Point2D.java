package java.awt.geom;
public abstract class Point2D {
    public abstract double getX();
    public abstract double getY();
    public void setLocation(double x, double y) {}
    public void setLocation(Point2D p) { setLocation(p.getX(), p.getY()); }
    public double distanceSq(double px, double py) {
        double dx = px - getX();
        double dy = py - getY();
        return dx * dx + dy * dy;
    }
    public double distance(double px, double py) { return Math.sqrt(distanceSq(px, py)); }
    public double distanceSq(Point2D pt) { return distanceSq(pt.getX(), pt.getY()); }
    public double distance(Point2D pt) { return distance(pt.getX(), pt.getY()); }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Point2D)) return false;
        Point2D p = (Point2D) other;
        return getX() == p.getX() && getY() == p.getY();
    }
    public int hashCode() {
        long bits = java.lang.Double.doubleToLongBits(getX());
        bits ^= java.lang.Double.doubleToLongBits(getY()) * 31;
        return (int) (bits ^ (bits >>> 32));
    }
    public static class Double extends Point2D {
        public double x;
        public double y;
        public Double() {}
        public Double(double x, double y) { this.x = x; this.y = y; }
        public double getX() { return x; }
        public double getY() { return y; }
        public void setLocation(double x, double y) { this.x = x; this.y = y; }
        public String toString() { return "Point2D.Double[" + x + ", " + y + "]"; }
    }
    public static class Float extends Point2D {
        public float x;
        public float y;
        public Float() {}
        public Float(float x, float y) { this.x = x; this.y = y; }
        public double getX() { return x; }
        public double getY() { return y; }
        public void setLocation(double x, double y) { this.x = (float) x; this.y = (float) y; }
    }
}