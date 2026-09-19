package java.awt;

/**
 * 最小桩：POI 的 `org.apache.poi.sl.draw.DrawPaint` 在方法签名里引用
 * `java.awt.Paint`，Android 平台没有该类，缺少它会让 DrawPaint 链接失败
 * （实测：读 PPT 形状填充时抛 NoClassDefFoundError，导致填充/描边被静默丢弃）。
 */
public interface Paint {
}
