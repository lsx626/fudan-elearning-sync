package java.awt;

/**
 * 最小桩：DrawPaint 的绘制方法签名引用 `java.awt.Graphics2D`。
 *
 * 应用内渲染走的是我们自己的 Canvas（PageRenderer），不会调用这里的方法；
 * 该桩只为让 POI 的 draw 包能在 ART 上完成类链接。
 */
public class Graphics2D {
}
