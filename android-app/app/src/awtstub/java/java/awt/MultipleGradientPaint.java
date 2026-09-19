package java.awt;

/** 最小桩：POI 的渐变绘制路径引用本类及其两个枚举。 */
public abstract class MultipleGradientPaint implements Paint {
    public enum ColorSpaceType {
        SRGB, LINEAR_RGB
    }

    public enum CycleMethod {
        NO_CYCLE, REFLECT, REPEAT
    }
}
