package java.awt.geom;
public abstract class RectangularShape {
    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public double getMinX() { return getX(); }
    public double getMinY() { return getY(); }
    public double getMaxX() { return getX() + getWidth(); }
    public double getMaxY() { return getY() + getHeight(); }
    public double getCenterX() { return getX() + getWidth() / 2.0; }
    public double getCenterY() { return getY() + getHeight() / 2.0; }
    public abstract boolean isEmpty();
    public abstract void setFrame(double x, double y, double w, double h);
    public void setFrame(Point2D loc, Dimension2D size) { setFrame(loc.getX(), loc.getY(), size.getWidth(), size.getHeight()); }
    public Rectangle2D getBounds2D() { return new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight()); }
}