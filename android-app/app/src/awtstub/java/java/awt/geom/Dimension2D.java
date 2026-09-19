package java.awt.geom;
public abstract class Dimension2D {
    public abstract double getWidth();
    public abstract double getHeight();
    public void setSize(double width, double height) {}
    public void setSize(Dimension2D d) { setSize(d.getWidth(), d.getHeight()); }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Dimension2D)) return false;
        Dimension2D d = (Dimension2D) other;
        return getWidth() == d.getWidth() && getHeight() == d.getHeight();
    }
    public int hashCode() {
        long bits = java.lang.Double.doubleToLongBits(getWidth()) ^ java.lang.Double.doubleToLongBits(getHeight());
        return (int) (bits ^ (bits >>> 32));
    }
    public static class Double extends Dimension2D {
        public double width;
        public double height;
        public Double() {}
        public Double(double width, double height) { this.width = width; this.height = height; }
        public Double(Dimension2D d) { this(d.getWidth(), d.getHeight()); }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setSize(double width, double height) { this.width = width; this.height = height; }
    }
    public static class Float extends Dimension2D {
        public float width;
        public float height;
        public Float() {}
        public Float(float width, float height) { this.width = width; this.height = height; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setSize(double width, double height) { this.width = (float) width; this.height = (float) height; }
    }
}