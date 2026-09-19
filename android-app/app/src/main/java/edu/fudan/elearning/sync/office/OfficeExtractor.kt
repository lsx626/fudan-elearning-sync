package edu.fudan.elearning.sync.office

import android.content.Context
import java.io.File

/**
 * Office 文档统一入口：按扩展名分发到幻灯片/文档/表格提取器，返回可直接绘制
 * 的 [DocPage] 列表。解析在调用方提供的 IO 线程执行。
 *
 * 结果语义明确区分：
 * - [OfficeParseResult.Success]：成功解析出一页或多页。
 * - [OfficeParseResult.Empty]：文件能打开但没有任何可渲染内容（空文档/空表）。
 * - [OfficeParseResult.Unsupported]：扩展名不在支持范围内。
 * - [OfficeParseResult.Failed]：文件损坏、加密或解析抛异常，附带原因。
 */
object OfficeExtractor {

    /** 设备屏幕宽度（px）作为渲染目标宽度。 */
    fun renderWidth(context: Context): Int {
        val dm = context.resources.displayMetrics
        return (dm.widthPixels * 1.6f).toInt().coerceIn(1080, 2560)
    }

    /** 判断是否为可渲染的 Office 文档。 */
    fun isOffice(name: String): Boolean {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "ppt", "pptx", "doc", "docx", "xls", "xlsx" -> true
            else -> false
        }
    }

    /**
     * 解析 Office 文档为页面列表，返回带明确语义的 [OfficeParseResult]。
     *
     * 所有解析器内部已对单个形状/单元格做了容错，这里只在整体失败时报告错误；
     * 图表、SmartArt、OLE 嵌入等复杂元素不在渲染范围内，由 UI 层如实说明限制。
     */
    fun extract(context: Context, file: File): OfficeParseResult {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (!isOffice(file.name)) return OfficeParseResult.Unsupported(ext)
        if (!file.exists() || !file.isFile) return OfficeParseResult.Failed("文件不存在或不是普通文件")

        val targetW = renderWidth(context)
        return runCatching {
            when (ext) {
                "ppt", "pptx" -> SlideExtractor.extract(file, targetW)
                "doc", "docx" -> {
                    val flow = WordExtractor.extract(file, targetW)
                    PageRenderer.paginate(flow)
                }
                "xls", "xlsx" -> SheetExtractor.extract(file, targetW)
                else -> return OfficeParseResult.Unsupported(ext)
            }
        }.fold(
            onSuccess = { pages ->
                if (pages.isEmpty()) {
                    OfficeParseResult.Empty("文档是空的，没有可显示的页面")
                } else {
                    OfficeParseResult.Success(pages)
                }
            },
            onFailure = {
                OfficeParseResult.Failed(
                    if (it.message.isNullOrBlank()) "文档解析失败，文件可能已损坏" else "文档解析失败：${it.message}"
                )
            }
        )
    }
}

/** Office 解析结果：成功/空/不支持/失败，调用方据此显示明确界面。 */
sealed class OfficeParseResult {
    data class Success(val pages: List<DocPage>) : OfficeParseResult()
    data class Empty(val reason: String) : OfficeParseResult()
    data class Unsupported(val ext: String) : OfficeParseResult()
    data class Failed(val message: String) : OfficeParseResult()
}