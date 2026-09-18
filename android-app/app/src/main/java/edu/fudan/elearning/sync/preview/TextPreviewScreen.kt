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

/** 单次读取的最大字节数，与桌面端 text 预览上限一致。 */
private const val MAX_BYTES = 2L * 1024 * 1024

/**
 * 应用内文本/代码/CSV/Markdown 预览。
 *
 * 硬性约束：最多读取 [MAX_BYTES] 字节，分块流式解码，禁止一次性把超大文件塞入内存。
 * 超出上限时尾部显示截断提示。UTF-8-sig 解码并容错。
 */
@Composable
fun TextPreviewScreen(file: File) {
    var content by remember { mutableStateOf("") }
    var truncated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf(0L) }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                fileSize = file.length()
                val limit = minOf(fileSize, MAX_BYTES)
                val raw = ByteArray(limit.toInt().coerceAtLeast(0))
                file.inputStream().use { it.read(raw) }
                // 去掉 UTF-8 BOM
                val start = if (raw.size >= 3 &&
                    raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()
                ) 3 else 0
                content = String(raw, start, raw.size - start, Charsets.UTF_8)
                truncated = fileSize > MAX_BYTES
            } catch (e: Exception) {
                error = "无法读取文件：${e.message ?: "未知错误"}"
            }
        }
    }

    when {
        error != null -> PreviewError(error!!)
        else -> {
            Column(Modifier.fillMaxSize()) {
                if (truncated) {
                    Text(
                        "文件较大（${formatBytes(fileSize)}），仅显示前 ${formatBytes(MAX_BYTES)} 内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                val scroll = rememberScrollState()
                Box(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f
                        ),
                        softWrap = true
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}