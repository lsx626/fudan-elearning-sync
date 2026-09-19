package edu.fudan.elearning.sync.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import edu.fudan.elearning.sync.office.OfficePreviewScreen
import java.io.File

/**
 * 统一预览路由：文件存在性/权限检查 -> [FileTypes] 类型识别 -> 分发到具体预览。
 *
 * 这是 FileUtils.openFile() 的应用内目标，取代原来的 ACTION_VIEW 跳转。
 * 保留分享入口（仅授予临时只读 URI 权限）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    file: File,
    onBack: () -> Unit,
    onShare: (File) -> Unit
) {
    val kind = remember(file.absolutePath) {
        if (!file.exists()) PreviewKind.UNSUPPORTED else FileTypes.detect(file)
    }
    val canPreview = file.exists() && kind != PreviewKind.UNSUPPORTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (file.exists()) {
                        IconButton(onClick = { onShare(file) }) {
                            Icon(Icons.Filled.Share, contentDescription = "分享文件")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !file.exists() -> PreviewError("文件不存在，可能尚未下载或已被删除。")
                !canPreview -> PreviewError(
                    message = "暂不支持在应用内预览此格式（" +
                        FileTypes.extOf(file.name).ifEmpty { "未知类型" } +
                        "）。可通过右上角分享，交给其他应用打开。",
                    title = "不支持的格式"
                )
                else -> when (kind) {
                    PreviewKind.PDF -> PdfPreviewScreen(file)
                    PreviewKind.IMAGE -> ImagePreviewScreen(file)
                    PreviewKind.TEXT -> TextPreviewScreen(file)
                    PreviewKind.MEDIA -> MediaPreviewScreen(file)
                    PreviewKind.OFFICE -> OfficePreviewScreen(file)
                    PreviewKind.STRUCTURED -> OfficeFallbackScreen(file)
                    PreviewKind.UNSUPPORTED -> PreviewError("不支持的格式")
                }
            }
        }
    }
}