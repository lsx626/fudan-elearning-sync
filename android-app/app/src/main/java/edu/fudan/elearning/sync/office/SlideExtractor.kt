package edu.fudan.elearning.sync.office

import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.sl.usermodel.ColorStyle
import org.apache.poi.sl.usermodel.PaintStyle
import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.sl.usermodel.PictureShape
import org.apache.poi.sl.usermodel.Slide
import org.apache.poi.sl.usermodel.TableCell
import org.apache.poi.sl.usermodel.TableShape
import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.sl.usermodel.TextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.awt.Color
import java.awt.Dimension
import java.awt.geom.Rectangle2D
import java.io.File

/**
 * 把 PPT/PPTX 解析成 [DocPage] 列表：每张幻灯片一页，保留形状坐标、文本格式、
 * 图片与表格，绘制时按比例还原版式（而非只提取文字）。
 *
 * 单位约定（POI 5.2.5）：[SlideShow.getPageSize]、[Shape.getAnchor]、字号与
 * [TextParagraph.getSpaceAfter] 均以磅为单位。页面尺寸直接按磅缩放到目标像素
 * 宽度，形状/字号坐标再乘同一个「磅→像素」因子，比例即保持一致。
 */
object SlideExtractor {

    /** 默认正文字号（磅），PPT 中未显式指定时用此值。 */
    private const val DEFAULT_FONT_PT = 18.0

    fun extract(file: File, targetWidthPx: Int): List<DocPage> {
        val name = file.name.lowercase()
        // 打开阶段抛出的异常（损坏/加密）交给 OfficeExtractor 报告为「解析失败」。
        val show = if (name.endsWith(".pptx")) {
            XMLSlideShow(file.inputStream())
        } else {
            HSLFSlideShow(file.inputStream())
        }
        return show.use { extractSlides(it.slides, it.pageSize, targetWidthPx) }
    }

    private fun extractSlides(
        slides: List<Slide<*, *>>,
        pageSize: Dimension,
        targetWidthPx: Int
    ): List<DocPage> {
        if (slides.isEmpty()) return emptyList()
        val pageWpt = pageSize.width.toDouble()
        val pageHpt = pageSize.height.toDouble()
        // 缩放因子 = 每磅对应像素
        val scale = targetWidthPx.toDouble() / pageWpt
        // 四舍五入，避免 16:9 等比例高度被系统性截断（如 607.5 -> 608）
        val pageHPx = Math.round(pageHpt * scale).coerceAtLeast(1).toInt()
        return slides.map { slide ->
            val items = mutableListOf<PageItem>()
            // 幻灯片背景色：FillStyle.getPaint() 为 ColorStyle 时取实色，否则用白色
            val bg = runCatching {
                paintArgb(slide.background?.fillStyle?.paint)
            }.getOrNull()
            items.add(PageItem.Fill(
                Rect4(0f, 0f, targetWidthPx.toFloat(), pageHPx.toFloat()),
                bg ?: 0xFFFFFFFF
            ))
            for (shape in slide.shapes) {
                runCatching { appendShape(shape, items, scale) }
            }
            DocPage(targetWidthPx, pageHPx, items)
        }
    }

    private fun appendShape(shape: Any, items: MutableList<PageItem>, scale: Double) {
        when (shape) {
            is XSLFPictureShape -> appendPicture(shape, items, scale)
            is HSLFPictureShape -> appendPicture(shape, items, scale)
            is XSLFTextShape -> appendTextShape(shape, items, scale)
            is HSLFTextShape -> appendTextShape(shape, items, scale)
            is XSLFTable -> appendTable(shape, items, scale)
            is HSLFTable -> appendTable(shape, items, scale)
        }
    }

    private fun appendPicture(shape: PictureShape<*, *>,
                              items: MutableList<PageItem>, scale: Double) {
        val data: PictureData? = runCatching { shape.pictureData }.getOrNull()
        if (data == null) return
        val rect = scaleRect(shape.anchor, scale) ?: return
        // 位图解码按魔数识别格式，MIME 仅记录用途，不需要精确值
        items.add(PageItem.Image(rect, data.data, "image/*"))
    }

    private fun appendTextShape(shape: TextShape<*, *>,
                                items: MutableList<PageItem>, scale: Double) {
        val rect = scaleRect(shape.anchor, scale) ?: return
        // 形状底色（实色填充）
        runCatching {
            colorArgb(shape.fillColor)?.let { items.add(PageItem.Fill(rect, it)) }
        }
        val paragraphs = shape.textParagraphs.mapNotNull { para ->
            runCatching { convertParagraph(para, scale) }.getOrNull()
        }
        if (paragraphs.isEmpty()) return
        items.add(PageItem.TextBlock(rect, paragraphs))
    }

    private fun appendTable(shape: TableShape<*, *>, items: MutableList<PageItem>, scale: Double) {
        val rect = scaleRect(shape.anchor, scale) ?: return
        val rows = mutableListOf<DocRow>()
        val numRows = runCatching { shape.numberOfRows }.getOrDefault(0)
        val numCols = runCatching { shape.numberOfColumns }.getOrDefault(0)
        for (r in 0 until numRows) {
            val cells = mutableListOf<DocCell>()
            var rowH = 0.0
            for (c in 0 until numCols) {
                runCatching {
                    val cell: TableCell<*, *> = shape.getCell(r, c)
                    val text = cell.text ?: ""
                    val sizePt = runCatching {
                        cell.textParagraphs.firstOrNull()?.textRuns?.firstOrNull()?.fontSize
                    }.getOrNull() ?: 12.0
                    val color = runCatching {
                        cell.textParagraphs.firstOrNull()?.textRuns?.firstOrNull()
                            ?.fontColor?.let { paintArgb(it) }
                    }.getOrNull()
                    val fill = runCatching { colorArgb(cell.fillColor) }.getOrNull()
                    cells.add(DocCell(text, color, fill, (sizePt * scale).toFloat()))
                    // 行高按单元高度（磅）换算
                    val ch = runCatching { cell.anchor.height * scale }.getOrDefault(0.0)
                    if (ch > rowH) rowH = ch
                }
            }
            rows.add(DocRow(cells, rowH.toFloat()))
        }
        if (rows.isNotEmpty()) items.add(PageItem.Table(rect, rows))
    }

    private fun convertParagraph(para: TextParagraph<*, *, *>, scale: Double): DocParagraph {
        val runs = para.textRuns.mapNotNull { run ->
            runCatching {
                val sizePt = run.fontSize ?: DEFAULT_FONT_PT
                DocRun(
                    text = run.rawText ?: "",
                    sizePx = (sizePt * scale).toFloat(),
                    argb = paintArgb(run.fontColor),
                    bold = run.isBold,
                    italic = run.isItalic,
                    underline = run.isUnderlined,
                    family = run.fontFamily
                )
            }.getOrNull()
        }.filter { it.text.isNotEmpty() }
        if (runs.isEmpty()) return DocParagraph(emptyList())
        val align = when (runCatching { para.textAlign }.getOrNull()) {
            TextParagraph.TextAlign.CENTER -> DocAlign.CENTER
            TextParagraph.TextAlign.RIGHT -> DocAlign.END
            TextParagraph.TextAlign.JUSTIFY, TextParagraph.TextAlign.DIST -> DocAlign.JUSTIFY
            else -> DocAlign.START
        }
        val spaceAfter = runCatching { (para.spaceAfter ?: 0.0) * scale }.getOrDefault(0.0)
        return DocParagraph(runs, align, spaceAfterPx = spaceAfter.toFloat())
    }

    /** AWT Rectangle2D（磅）→ 像素 Rect4。 */
    private fun scaleRect(anchor: Rectangle2D, scale: Double): Rect4? {
        val w = anchor.width
        val h = anchor.height
        if (w <= 0 || h <= 0) return null
        return Rect4(
            (anchor.x * scale).toFloat(),
            (anchor.y * scale).toFloat(),
            ((anchor.x + w) * scale).toFloat(),
            ((anchor.y + h) * scale).toFloat()
        )
    }

    /** PaintStyle → ARGB；仅处理 ColorStyle（实色），渐变/图案/图片填充返回 null。 */
    private fun paintArgb(paint: PaintStyle?): Long? {
        if (paint == null || paint !is ColorStyle) return null
        return runCatching {
            paint.color?.let { colorArgb(it) }
        }.getOrNull()
    }

    /** java.awt.Color → ARGB Long。 */
    private fun colorArgb(color: Color?): Long? {
        if (color == null) return null
        return (0xFFL shl 24) or (color.rgb.toLong() and 0xFFFFFF)
    }
}