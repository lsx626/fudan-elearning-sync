package edu.fudan.elearning.sync.office

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import kotlin.math.roundToInt

/**
 * 把 [DocPage] 绘制成位图，并把 Word 的 [FlowDocument] 按真实文本测量分页。
 *
 * 绘制策略：
 * - 文本用 StaticLayout（支持换行与富文本 Span），位置由模型决定；
 * - 图片解码后按目标矩形等比缩放；
 * - 表格逐行绘制单元文本、底色与网格线。
 */
object PageRenderer {

    /** ARGB Long → Int 颜色。 */
    private fun Long.toIntColor(): Int = this.toInt()

    /**
     * Word 流式文档分页：按段落真实高度切页，尽量贴近原阅读器版式。
     */
    fun paginate(doc: FlowDocument): List<DocPage> {
        if (doc.blocks.isEmpty()) {
            return listOf(DocPage(doc.pageWidthPx, doc.pageHeightPx,
                listOf(PageItem.Fill(Rect4(0f, 0f, doc.pageWidthPx.toFloat(), doc.pageHeightPx.toFloat()), 0xFFFFFFFF))))
        }
        val pages = mutableListOf<DocPage>()
        val contentW = (doc.pageWidthPx - 2 * doc.marginPx).toFloat()
        var items = mutableListOf<PageItem>()
        var y = doc.marginPx

        fun flushPage() {
            pages.add(DocPage(doc.pageWidthPx, doc.pageHeightPx, items))
            items = mutableListOf()
            y = doc.marginPx
        }

        for (block in doc.blocks) {
            when (block) {
                is FlowBlock.Paragraph -> {
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
                    val firstRun = block.para.runs.firstOrNull()
                    paint.textSize = firstRun?.sizePx ?: 13f
                    paint.color = (firstRun?.argb ?: 0xFF1A1A1A).toIntColor()
                    val sb = buildSpannable(block.para)
                    val layout = StaticLayout.Builder.obtain(
                        sb, 0, sb.length, paint, contentW.roundToInt()
                    ).setAlignment(alignOf(block.para.align))
                        .setLineSpacing(0f, 1.25f)
                        .build()
                    val h = layout.height + block.para.spaceAfterPx
                    if (y + h > doc.pageHeightPx - doc.marginPx && items.isNotEmpty()) {
                        flushPage()
                    }
                    val rect = Rect4(doc.marginPx, y, doc.pageWidthPx - doc.marginPx, y + h)
                    items.add(PageItem.TextBlock(rect, listOf(block.para)))
                    y += h
                }
                is FlowBlock.Picture -> {
                    var w = block.widthPx.toFloat()
                    var h = block.heightPx.toFloat()
                    if (w > contentW) {
                        val r = contentW / w
                        w *= r; h *= r
                    }
                    if (y + h > doc.pageHeightPx - doc.marginPx && items.isNotEmpty()) {
                        flushPage()
                    }
                    items.add(PageItem.Image(
                        Rect4(doc.marginPx, y, doc.marginPx + w, y + h),
                        block.bytes, block.mime
                    ))
                    y += h + 8f
                }
                is FlowBlock.Spacer -> {
                    y += block.heightPx
                }
            }
            if (y > doc.pageHeightPx - doc.marginPx && items.isNotEmpty()) {
                flushPage()
            }
        }
        if (items.isNotEmpty()) flushPage()
        return pages
    }

    private fun alignOf(align: DocAlign): Layout.Alignment = when (align) {
        DocAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        DocAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        DocAlign.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
        DocAlign.START -> Layout.Alignment.ALIGN_NORMAL
    }

    /** 拼接富文本：字号/颜色/加粗斜体/下划线。 */
    private fun buildSpannable(para: DocParagraph): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (run in para.runs) {
            val start = sb.length
            sb.append(run.text)
            val end = sb.length
            sb.setSpan(AbsoluteSizeSpan(run.sizePx.roundToInt()), start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            run.argb?.let {
                sb.setSpan(ForegroundColorSpan(it.toIntColor()), start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.bold && run.italic) {
                sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (run.bold) {
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (run.italic) {
                sb.setSpan(StyleSpan(Typeface.ITALIC), start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.underline) {
                sb.setSpan(UnderlineSpan(), start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    /** 把一页绘制成位图（在 IO 线程调用）。 */
    fun renderPage(page: DocPage): Bitmap {
        val bmp = Bitmap.createBitmap(page.widthPx, page.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        for (item in page.items) {
            when (item) {
                is PageItem.Fill -> {
                    val p = Paint().apply { color = item.argb.toIntColor() }
                    canvas.drawRect(item.rect.toRectF(), p)
                }
                is PageItem.Image -> drawImage(canvas, item)
                is PageItem.TextBlock -> drawText(canvas, item)
                is PageItem.Table -> drawTable(canvas, item)
                is PageItem.Line -> {
                    val p = Paint().apply {
                        color = item.argb.toIntColor()
                        strokeWidth = item.widthPx
                        isAntiAlias = true
                    }
                    canvas.drawLine(item.x1, item.y1, item.x2, item.y2, p)
                }
            }
        }
        return bmp
    }

    private fun drawImage(canvas: Canvas, item: PageItem.Image) {
        runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(item.bytes, 0, item.bytes.size, opts)
            val sample = (opts.outWidth / item.rect.width).toInt().coerceAtLeast(1)
            val opts2 = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            val src = android.graphics.BitmapFactory.decodeByteArray(item.bytes, 0, item.bytes.size, opts2)
                ?: return@runCatching
            val srcW = src.width.toFloat()
            val srcH = src.height.toFloat()
            val target = item.rect.toRectF()
            // 保持比例，适配目标矩形
            val scale = minOf(target.width() / srcW, target.height() / srcH)
            val dw = srcW * scale
            val dh = srcH * scale
            val left = target.left + (target.width() - dw) / 2f
            val top = target.top + (target.height() - dh) / 2f
            val dst = RectF(left, top, left + dw, top + dh)
            canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun drawText(canvas: Canvas, item: PageItem.TextBlock) {
        var y = item.rect.top
        for (para in item.paragraphs) {
            if (para.runs.isEmpty()) {
                y += para.spaceAfterPx
                continue
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            val first = para.runs.first()
            paint.textSize = first.sizePx
            paint.color = (first.argb ?: 0xFF1A1A1A).toIntColor()
            val sb = buildSpannable(para)
            val w = (item.rect.width).roundToInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(sb, 0, sb.length, paint, w)
                .setAlignment(alignOf(para.align))
                .build()
            canvas.save()
            canvas.translate(item.rect.left, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + para.spaceAfterPx
            if (y > item.rect.bottom) break
        }
    }

    private fun drawTable(canvas: Canvas, table: PageItem.Table) {
        var y = table.rect.top
        for (row in table.rows) {
            var x = table.rect.left
            // 按行内单元数均分宽度
            val cellW = table.rect.width / row.cells.size.coerceAtLeast(1)
            for (cell in row.cells) {
                val rectF = RectF(x, y, x + cellW, y + row.heightPx)
                cell.fill?.let {
                    canvas.drawRect(rectF, Paint().apply { color = it.toIntColor() })
                }
                // 边框
                canvas.drawRect(rectF, Paint().apply {
                    color = 0xFFC8CDD2.toIntColor()
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                })
                if (cell.text.isNotEmpty()) {
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
                    paint.textSize = cell.sizePx
                    paint.color = (cell.argb ?: 0xFF1A1A1A).toIntColor()
                    paint.isFakeBoldText = cell.bold
                    val sb = SpannableStringBuilder(cell.text)
                    val layout = StaticLayout.Builder.obtain(
                        sb, 0, sb.length, paint, (cellW - 6f).roundToInt().coerceAtLeast(1)
                    ).build()
                    canvas.save()
                    canvas.translate(x + 3f, y + (row.heightPx - layout.height) / 2f)
                    layout.draw(canvas)
                    canvas.restore()
                }
                x += cellW
            }
            y += row.heightPx
            if (y > table.rect.bottom) break
        }
    }

    private fun Rect4.toRectF(): RectF = RectF(left, top, right, bottom)
}