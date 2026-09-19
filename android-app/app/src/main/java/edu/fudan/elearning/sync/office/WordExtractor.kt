package edu.fudan.elearning.sync.office

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.usermodel.Range
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFPicture
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.File

/**
 * 把 Word（docx/doc）解析成 [FlowDocument]：保留段落文本、加粗/斜体/字号/颜色与
 * 内嵌图片；分页由 Android 侧按真实文本测量完成，版式接近原阅读器。
 *
 * .doc（旧二进制 OLE2）用 POI scratchpad 的 HWPF；HWPF 的图片提取不可靠，
 * 旧格式只保证正文文字与基本格式。
 *
 * 页面按 A4 纵向、以 [targetWidthPx] 等比缩放，避免按 DPI 渲染出超大位图导致 OOM。
 */
object WordExtractor {

    /** A4 纵向，单位磅（72dpi 近似：595x842pt）。 */
    private const val A4_W_PT = 595f
    private const val A4_H_PT = 842f
    private const val MARGIN_PT = 56f
    private const val EMU_PER_PT = 12700f

    fun extract(file: File, targetWidthPx: Int): FlowDocument {
        val name = file.name.lowercase()
        val scale = targetWidthPx.toFloat() / A4_W_PT
        // 打开阶段抛出的异常（损坏/加密）交给 OfficeExtractor 报告为「解析失败」。
        return if (name.endsWith(".docx")) {
            XWPFDocument(file.inputStream()).use { extractX(it, scale) }
        } else {
            HWPFDocument(file.inputStream()).use { extractH(it, scale) }
        }
    }

    private fun extractX(doc: XWPFDocument, scale: Float): FlowDocument {
        val blocks = mutableListOf<FlowBlock>()
        for (para in doc.paragraphs) {
            val runs = para.runs.mapNotNull { run ->
                runCatching { convertRun(run, scale) }.getOrNull()
            }.filter { it.text.isNotEmpty() }
            // 内嵌图片：取 run 中的图片，按原始尺寸（EMU→px）输出
            for (pic in para.runs.flatMap { run ->
                runCatching { run.embeddedPictures }.getOrDefault(emptyList())
            }) {
                addPicture(pic, blocks, scale)
            }
            if (runs.isEmpty()) {
                blocks.add(FlowBlock.Spacer(8f * scale))
                continue
            }
            val align = when (runCatching { para.alignment }.getOrNull()) {
                ParagraphAlignment.CENTER -> DocAlign.CENTER
                ParagraphAlignment.RIGHT, ParagraphAlignment.END -> DocAlign.END
                ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> DocAlign.JUSTIFY
                else -> DocAlign.START
            }
            blocks.add(FlowBlock.Paragraph(
                DocParagraph(runs, align, spaceAfterPx = 4f * scale)
            ))
        }
        return flowDoc(blocks, scale)
    }

    private fun extractH(doc: HWPFDocument, scale: Float): FlowDocument {
        val blocks = mutableListOf<FlowBlock>()
        val range: Range = doc.range
        for (i in 0 until range.numParagraphs()) {
            val p = runCatching { range.getParagraph(i) }.getOrNull() ?: continue
            val text = runCatching { p.text()?.trim() }.getOrNull() ?: ""
            if (text.isEmpty()) {
                blocks.add(FlowBlock.Spacer(8f * scale))
                continue
            }
            // HWPF 字符级样式通过 CharacterRun 获取；这里按段落取首个 CharacterRun 的格式近似
            val cr = if (p.numCharacterRuns() > 0) runCatching { p.getCharacterRun(0) }.getOrNull() else null
            val sizePt = (runCatching { cr?.fontSize }.getOrNull() ?: 22) / 2f   // HWPF fontSize 单位为半磅
            val bold = runCatching { cr?.isBold }.getOrDefault(false) == true
            val italic = runCatching { cr?.isItalic }.getOrDefault(false) == true
            val argb = cr?.let {
                runCatching {
                    val rgb = it.color
                    if (rgb >= 0) (0xFFL shl 24) or (rgb.toLong() and 0xFFFFFF) else null
                }.getOrNull()
            }
            blocks.add(FlowBlock.Paragraph(
                DocParagraph(
                    listOf(DocRun(text, sizePt * scale, argb, bold, italic)),
                    spaceAfterPx = 4f * scale
                )
            ))
        }
        return flowDoc(blocks, scale)
    }

    private fun addPicture(pic: XWPFPicture, blocks: MutableList<FlowBlock>, scale: Float) {
        runCatching {
            // XWPFPicture.getWidth()/getDepth() 返回 EMU（wp:extent 的 cx/cy）
            val wEmu = pic.width
            val hEmu = pic.depth
            if (wEmu <= 0 || hEmu <= 0) return@runCatching
            // EMU → 磅 → 像素（页面按 targetWidthPx/A4 宽度缩放）
            val wPx = (wEmu / EMU_PER_PT * scale).toInt().coerceAtLeast(1)
            val hPx = (hEmu / EMU_PER_PT * scale).toInt().coerceAtLeast(1)
            val data = pic.pictureData ?: return@runCatching
            blocks.add(FlowBlock.Picture(data.data, "image/*", wPx, hPx))
        }
    }

    private fun convertRun(run: XWPFRun, scale: Float): DocRun? {
        val text = run.text() ?: ""
        if (text.isEmpty()) return null
        // XWPFRun.getFontSize() 返回 int（未设置时为 -1），正数时为磅值
        val sizePt = run.fontSize.takeIf { it > 0 }?.toDouble() ?: 12.0
        val argb = runCatching {
            val rgb = run.color
            if (!rgb.isNullOrBlank() && rgb.length >= 6) {
                (0xFFL shl 24) or rgb.substring(0, 6).toLong(16)
            } else null
        }.getOrNull()
        return DocRun(
            text = text,
            sizePx = (sizePt * scale).toFloat(),
            argb = argb,
            bold = run.isBold,
            italic = run.isItalic,
            underline = run.underline != UnderlinePatterns.NONE
        )
    }

    private fun flowDoc(blocks: List<FlowBlock>, scale: Float): FlowDocument {
        return FlowDocument(
            pageWidthPx = (A4_W_PT * scale).toInt(),
            pageHeightPx = (A4_H_PT * scale).toInt(),
            marginPx = MARGIN_PT * scale,
            blocks = blocks
        )
    }
}