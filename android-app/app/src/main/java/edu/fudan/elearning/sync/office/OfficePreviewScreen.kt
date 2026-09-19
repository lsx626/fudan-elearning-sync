package edu.fudan.elearning.sync.office

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import edu.fudan.elearning.sync.preview.PreviewError
import edu.fudan.elearning.sync.preview.VerticalPageList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Office 文档预览（doc/docx/ppt/pptx/xls/xlsx）：POI 解析为页模型后逐页位图渲染，
 * 纵向连续滚动（下拉式漫画体验），每页支持双指/双击缩放。
 *
 * 解析失败、空文档或不支持的格式会显示明确说明；复杂元素（图表、SmartArt、OLE
 * 嵌入等）不保证还原，在列表顶部以提示卡如实说明。
 */
@Composable
fun OfficePreviewScreen(file: File) {
    var result by remember(file.absolutePath) { mutableStateOf<OfficeParseResult?>(null) }
    val context = LocalContext.current

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            result = OfficeExtractor.extract(context, file)
        }
    }

    when (val r = result) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is OfficeParseResult.Success -> {
            val pages = r.pages
            VerticalPageList(
                pageCount = pages.size,
                aspectOf = { index ->
                    val p = pages[index]
                    if (p.heightPx > 0) p.widthPx.toFloat() / p.heightPx else 0.75f
                },
                renderPage = { index -> renderOfficePage(pages, index) },
                header = { OfficeLimitationNote(file.name) }
            )
        }
        is OfficeParseResult.Empty -> PreviewError(r.reason, title = "没有可显示的内容")
        is OfficeParseResult.Unsupported -> PreviewError(
            message = "暂不支持在应用内预览此格式（${r.ext.ifEmpty { "未知类型" }}）。",
            title = "不支持的格式"
        )
        is OfficeParseResult.Failed -> PreviewError(r.message, title = "无法预览")
    }
}

/** 列表顶部的保真度说明：如实告知哪些内容可能不还原。 */
@Composable
private fun OfficeLimitationNote(name: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                "已按版式还原页面；图表、SmartArt、OLE 嵌入与动画等复杂元素可能不显示，" +
                    "可通过右上角分享给其他工具做高保真查看。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 渲染单页为位图；长边上限 [MAX_DIMEN]，防止位图过大。
 *
 * 提取器内部已按屏幕宽度限制页宽，这里对异常文档做长边兜底，避免生成超大位图。
 */
private fun renderOfficePage(pages: List<DocPage>, index: Int): Bitmap? = runCatching {
    if (index < 0 || index >= pages.size) return null
    val page = pages[index]
    if (page.widthPx > MAX_DIMEN || page.heightPx > MAX_DIMEN) return null
    PageRenderer.renderPage(page)
}.getOrNull()

private const val MAX_DIMEN = 4096
