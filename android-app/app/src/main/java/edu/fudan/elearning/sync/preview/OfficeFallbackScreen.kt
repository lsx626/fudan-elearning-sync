package edu.fudan.elearning.sync.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Office/ODF/压缩包等无法在应用内高保真渲染的格式的结构化降级预览。
 *
 * 策略（与桌面端“结构化解析降级”一致，且明确说明限制）：
 * 1. 对 OOXML（docx/xlsx/pptx）与 ODF（odt/ods/odp）做轻量文本抽取，
 *    只保证可读内容，不保证分页、图表、动画、宏和复杂布局保真。
 * 2. 清楚告知用户这是降级视图，可经分享/另存到其他工具做高保真处理。
 * 3. 旧二进制 .doc/.xls/.ppt 无法解析，直接说明不支持，不假装成功。
 */
@Composable
fun OfficeFallbackScreen(file: File, displayName: String = file.name) {
    val ext = FileTypes.extOf(file.name)
    var extracted by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            when (ext) {
                "docx", "pptx", "odt", "odp" -> {
                    note = "已提取正文文本（不含图片、图表、动画与复杂排版）"
                    extracted = extractText(file, setOf("word/document.xml", "ppt/slides", "content.xml"))
                }
                "xlsx", "ods" -> {
                    note = "已提取单元格文本（不含公式结果、样式与图表）"
                    extracted = extractText(file, setOf("xl/sharedStrings.xml", "xl/worksheets", "content.xml"))
                }
                else -> {
                    note = "该格式无法在应用内解析内容，可通过分享交给其他工具处理"
                    extracted = null
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            displayName.ifBlank { file.name },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2
        )
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        val text = extracted
        if (text.isNullOrBlank()) {
            Text(
                "未提取到可读文本。该文件可能是旧版二进制格式（如 .doc/.xls/.ppt），" +
                    "当前无法在应用内解析，请通过分享使用其他工具查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "降级预览 · 最多显示前 ${MAX_CHARS} 字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val scroll = rememberScrollState()
            Box(
                Modifier.fillMaxSize().verticalScroll(scroll)
            ) {
                Text(
                    text = text.take(MAX_CHARS),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private const val MAX_CHARS = 200_000

/**
 * 从 OOXML/ODF（本质是 zip）中按条目抽取纯文本：
 * 命中目标条目前缀时，剥离 XML 标签保留可见文字。
 */
private fun extractText(file: File, targets: Set<String>): String? {
    return try {
        val builder = StringBuilder()
        ZipInputStream(file.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (targets.any { name.startsWith(it) } && name.endsWith(".xml")) {
                    val raw = zis.bufferedReader(Charsets.UTF_8).readText()
                    val text = stripXml(raw)
                    if (text.isNotBlank()) {
                        if (builder.isNotEmpty()) builder.append("\n\n")
                        builder.append(text)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        builder.toString().ifBlank { null }
    } catch (e: Exception) {
        null
    }
}

/** 剥离 XML 标签，把 <w:p>/<a:p>/<w:br/> 等转成换行，保留可读文字。 */
private fun stripXml(xml: String): String {
    return xml
        // 段落/换行标记先转成换行符，避免后续被一并抹掉
        .replace(Regex("</(?:w:p|a:p|p:p|text:p)>"), "\n")
        .replace(Regex("<(?:w:br|w:tab|text:tab)[^>]*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}