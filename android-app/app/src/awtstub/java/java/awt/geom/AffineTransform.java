package java.awt.geom;

import java.awt.Shape;

/**
 * 最小桩：POI 的 DrawPaint / 渐变绘制路径引用本类。
 *
 * 只提供链接所需的构造与常用工厂方法；真正的几何变换由 PageRenderer 的
 * Canvas 完成，不会走到这里。
 */
public class AffineTransform {
    public AffineTransform() {
    }

    public AffineTransform(AffineTransform tx) {
    }

    public static AffineTransform getTranslateInstance(double tx, double ty) {
        return new AffineTransform();
    }

    public static AffineTransform getScaleInstance(double sx, double sy) {
        return new AffineTransform();
    }

    public static AffineTransform getRotateInstance(double theta) {
        return new AffineTransform();
    }

    public Shape createTransformedShape(Shape pSrc) {
        return pSrc;
    }

    public Point2D transform(Point2D ptSrc, Point2D ptDst) {
        return ptDst != null ? ptDst : ptSrc;
    }
}
