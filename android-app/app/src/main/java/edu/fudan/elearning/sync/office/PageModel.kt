package edu.fudan.elearning.sync.office

/**
 * Office 文档页模型（纯 Kotlin，不依赖 Android 平台 API）。
 *
 * 解析层把 doc/docx/ppt/pptx/xls/xlsx 转成一组「页面 + 页内元素」，
 * 再由 [PageRenderer] 用 Android Canvas 绘制成位图。
 * 这样解析逻辑可以在 JVM 单元测试中用真实合成夹具验证。
 */

/** 浮点矩形：left/top/right/bottom。 */
data class Rect4(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** 文本对齐。 */
enum class DocAlign { START, CENTER, END, JUSTIFY }

/** 一段文本中的一段连续格式片段。 */
data class DocRun(
    val text: String,
    /** 字号（像素）。 */
    val sizePx: Float,
    /** ARGB 颜色；null 表示用默认前景色。 */
    val argb: Long? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** 可选字体族（用于中文/等宽场景）。 */
    val family: String? = null
)

/** 一个段落：多段 Run 拼成一行文本。 */
data class DocParagraph(
    val runs: List<DocRun>,
    val align: DocAlign = DocAlign.START,
    /** 段后间距（像素）。 */
    val spaceAfterPx: Float = 0f,
    val bullet: Boolean = false
) {
    val text: String get() = runs.joinToString("") { it.text }
}

/** 页面元素。 */
sealed class PageItem {
    /** 纯色填充矩形（背景、表格底色等）。 */
    data class Fill(val rect: Rect4, val argb: Long) : PageItem()

    /** 图片：原始字节与 MIME，绘制时才解码。 */
    data class Image(val rect: Rect4, val bytes: ByteArray, val mime: String) : PageItem()

    /** 定位文本块（PPT 文本框、表格单元等）。 */
    data class TextBlock(val rect: Rect4, val paragraphs: List<DocParagraph>) : PageItem()

    /** 表格网格：按行列排布的单元文本。 */
    data class Table(val rect: Rect4, val rows: List<DocRow>) : PageItem()

    /** 直线（表格边框、分隔线等）。 */
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float,
                    val argb: Long, val widthPx: Float) : PageItem()
}

/** 表格的一行。 */
data class DocRow(
    val cells: List<DocCell>,
    val heightPx: Float
)

/** 表格单元。 */
data class DocCell(
    val text: String,
    val argb: Long? = null,
    val fill: Long? = null,
    val sizePx: Float = 14f,
    val bold: Boolean = false
)

/**
 * 一页：目标渲染尺寸与元素列表（元素坐标已换算成像素）。
 */
data class DocPage(
    val widthPx: Int,
    val heightPx: Int,
    val items: List<PageItem>
)

/**
 * 「流式」文档（doc/docx）：尚未分页的段落序列，由 Android 侧分页器按实际
 * 文本测量结果切分成 [DocPage]。
 */
data class FlowDocument(
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val marginPx: Float,
    val blocks: List<FlowBlock>
)

/** 流式文档块。 */
sealed class FlowBlock {
    data class Paragraph(val para: DocParagraph) : FlowBlock()
    data class Picture(val bytes: ByteArray, val mime: String,
                       val widthPx: Int, val heightPx: Int) : FlowBlock()
    data class Spacer(val heightPx: Float) : FlowBlock()
}