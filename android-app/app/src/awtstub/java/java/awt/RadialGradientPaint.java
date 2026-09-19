package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/** 最小桩：DrawPaint#createRadialGradientPaint 的返回类型。 */
public class RadialGradientPaint extends MultipleGradientPaint {
    public RadialGradientPaint(Point2D center, float radius, Point2D focus,
                               float[] fractions, Color[] colors, CycleMethod cycleMethod,
                               ColorSpaceType colorSpace, AffineTransform gradientTransform) {
    }
}
