package java.awt;
public class Color {
    int value;
    public Color(int rgb) { this.value = rgb & 0xFFFFFFFF; }
    /** `Color(int rgb, boolean hasAlpha)`：桩按已含 alpha 的 RGB 处理。 */
    public Color(int rgb, boolean hasAlpha) { this(rgb); }
    public Color(int r, int g, int b) { this(r, g, b, 255); }
    public Color(int r, int g, int b, int a) {
        value = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    public Color(float r, float g, float b) { this(r, g, b, 1.0f); }
    /**
     * 浮点分量构造器（0..1，超出范围抛 IllegalArgumentException，与 JDK 一致）。
     *
     * POI 的 `DrawPaint.<clinit>` 会用 `new Color(0f, 0f, 0f, 0f)` 建透明色；
     * 缺这个构造器会让 DrawPaint 类初始化失败，进而在真机上把**所有 PPT 的填充与
     * 描边静默丢弃**（插桩测试实测：NoSuchMethodError &lt;init&gt;(FFFF)V）。
     */
    public Color(float r, float g, float b, float a) {
        this(toByte(r), toByte(g), toByte(b), toByte(a));
    }
    private static int toByte(float v) {
        if (v < 0.0f || v > 1.0f) throw new IllegalArgumentException("Color parameter outside of expected range: " + v);
        return Math.round(v * 255.0f);
    }
    public int getRGB() { return value; }
    public int getRed() { return (value >> 16) & 0xFF; }
    public int getGreen() { return (value >> 8) & 0xFF; }
    public int getBlue() { return value & 0xFF; }
    public int getAlpha() { return (value >> 24) & 0xFF; }

    // ------------------------------------------------------------------
    // 分量访问器：POI 的写 API（XSLFColor.setColor / setFillColor 等）会调用
    // getRGBComponents/getRGBColorComponents 把 AWT Color 转成 DrawingML 的
    // 百分比色值。缺了它们会在设备上抛 NoSuchMethodError
    // （插桩测试实测：pptx_shapesRenderAndChartBecomesPlaceholder）。
    // 语义与 JDK 一致：数组为 null 时新建；默认 sRGB 色彩空间下分量归一化到 0..1。
    // ------------------------------------------------------------------

    /** RGB + alpha 四个分量（0..1），顺序与 JDK 相同：红、绿、蓝、透明度。 */
    public float[] getRGBComponents(float[] compArray) {
        float[] out = (compArray == null) ? new float[4] : compArray;
        out[0] = getRed() / 255f;
        out[1] = getGreen() / 255f;
        out[2] = getBlue() / 255f;
        out[3] = getAlpha() / 255f;
        return out;
    }

    /** 只含 RGB 三个分量（0..1），忽略 alpha；poi 写实色填充时用的是这个。 */
    public float[] getRGBColorComponents(float[] compArray) {
        float[] out = (compArray == null) ? new float[3] : compArray;
        out[0] = getRed() / 255f;
        out[1] = getGreen() / 255f;
        out[2] = getBlue() / 255f;
        return out;
    }

    /** 桩只实现 sRGB，分量与 getRGBComponents 相同（4 个分量）。 */
    public float[] getComponents(float[] compArray) { return getRGBComponents(compArray); }

    /** 桩只实现 sRGB，分量与 getRGBColorComponents 相同（3 个分量）。 */
    public float[] getColorComponents(float[] compArray) { return getRGBColorComponents(compArray); }

    public Color brighter() { return new Color(Math.min(255, getRed() + 32), Math.min(255, getGreen() + 32), Math.min(255, getBlue() + 32)); }
    public Color darker() { return new Color(Math.max(0, getRed() - 32), Math.max(0, getGreen() - 32), Math.max(0, getBlue() - 32)); }
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Color)) return false;
        return value == ((Color) other).value;
    }
    public int hashCode() { return value; }
    public String toString() { return "Color[r=" + getRed() + ",g=" + getGreen() + ",b=" + getBlue() + "]"; }
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color RED = new Color(255, 0, 0);
    public static final Color GREEN = new Color(0, 255, 0);
    public static final Color BLUE = new Color(0, 0, 255);

    // JDK 的小写常量字段：POI 内部直接用 Color.black / Color.white 这类常量
    // （实测 XSLFDrawing.createConnector 引用 Color.black），缺了会抛
    // NoSuchFieldError。取值与 JDK 的预定义颜色一致。
    public static final Color black = BLACK;
    public static final Color white = WHITE;
    public static final Color red = RED;
    public static final Color green = GREEN;
    public static final Color blue = BLUE;
    public static final Color cyan = new Color(0, 255, 255);
    public static final Color magenta = new Color(255, 0, 255);
    public static final Color yellow = new Color(255, 255, 0);
    public static final Color orange = new Color(255, 200, 0);
    public static final Color pink = new Color(255, 175, 175);
    public static final Color gray = new Color(128, 128, 128);
    public static final Color darkGray = new Color(64, 64, 64);
    public static final Color lightGray = new Color(192, 192, 192);
}
