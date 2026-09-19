package java.awt.geom;
public abstract class Rectangle2D extends RectangularShape {
    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public boolean isEmpty() { return getWidth() < 0 || getHeight() < 0; }
    public void setFrame(double x, double y, double w, double h) { setRect(x, y, w, h); }
    public void setRect(double x, double y, double w, double h) {}
    public void setRect(Rectangle2D r) { setRect(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public Rectangle2D getBounds2D() { return new Double(getX(), getY(), getWidth(), getHeight()); }
    public void add(Point2D pt) { add(pt.getX(), pt.getY()); }
    public void add(Rectangle2D r) {
        double x1 = Math.min(getX(), r.getX());
        double y1 = Math.min(getY(), r.getY());
        double x2 = Math.max(getX() + getWidth(), r.getX() + r.getWidth());
        double y2 = Math.max(getY() + getHeight(), r.getY() + r.getHeight());
        setRect(x1, y1, x2 - x1, y2 - y1);
    }
    public void add(double newx, double newy) {
        double x1 = Math.min(getX(), newx);
        double y1 = Math.min(getY(), newy);
        double x2 = Math.max(getX() + getWidth(), newx);
        double y2 = Math.max(getY() + getHeight(), newy);
        setRect(x1, y1, x2 - x1, y2 - y1);
    }
    public boolean contains(double x, double y) {
        return x >= getX() && x < getX() + getWidth() && y >= getY() && y < getY() + getHeight();
    }
    public boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w - 1, y + h - 1);
    }
    public boolean contains(Rectangle2D r) { return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public boolean intersects(double x, double y, double w, double h) {
        return x + w > getX() && x < getX() + getWidth() && y + h > getY() && y < getY() + getHeight();
    }
    public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public Rectangle2D createUnion(Rectangle2D r) {
        double x1 = Math.min(getX(), r.getX());
        double y1 = Math.min(getY(), r.getY());
        double x2 = Math.max(getX() + getWidth(), r.getX() + r.getWidth());
        double y2 = Math.max(getY() + getHeight(), r.getY() + r.getHeight());
        return new Double(x1, y1, x2 - x1, y2 - y1);
    }
    public Rectangle2D createIntersection(Rectangle2D r) {
        double x1 = Math.max(getX(), r.getX());
        double y1 = Math.max(getY(), r.getY());
        double x2 = Math.min(getX() + getWidth(), r.getX() + r.getWidth());
        double y2 = Math.min(getY() + getHeight(), r.getY() + r.getHeight());
        return new Double(x1, y1, x2 - x1, y2 - y1);
    }
    public static class Double extends Rectangle2D {
        public double x;
        public double y;
        public double width;
        public double height;
        public Double() {}
        public Double(double x, double y, double w, double h) { this.x = x; this.y = y; this.width = w; this.height = h; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setRect(double x, double y, double w, double h) { this.x = x; this.y = y; this.width = w; this.height = h; }
        public Rectangle2D getBounds2D() { return new Double(x, y, width, height); }
        public String toString() { return "Rectangle2D.Double[x=" + x + ",y=" + y + ",w=" + width + ",h=" + height + "]"; }
    }
    public static class Float extends Rectangle2D {
        public float x;
        public float y;
        public float width;
        public float height;
        public Float() {}
        public Float(float x, float y, float w, float h) { this.x = x; this.y = y; this.width = w; this.height = h; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setRect(double x, double y, double w, double h) { this.x = (float) x; this.y = (float) y; this.width = (float) w; this.height = (float) h; }
    }
}