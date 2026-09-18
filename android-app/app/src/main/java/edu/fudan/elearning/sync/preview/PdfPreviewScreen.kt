package edu.fudan.elearning.sync.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 应用内 PDF 预览：PdfRenderer 逐页位图渲染，支持多页滑动与双指缩放。
 *
 * 与桌面端 QPdfDocument 方案对齐：多页、适配宽度；渲染失败时显示结构化错误，
 * 不跳转第三方应用。
 */
@Composable
fun PdfPreviewScreen(file: File) {
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val bitmaps = remember(file.absolutePath) { mutableMapOf<Int, Bitmap>() }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        if (pageCount <= 0) error = "该 PDF 没有可显示的页面"
                    }
                }
            } catch (e: Exception) {
                error = "无法打开 PDF：${e.message ?: "文件可能已损坏"}"
            }
        }
    }

    when {
        error != null -> PreviewError(error!!)
        pageCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            val pagerState = rememberPagerState(pageCount = { pageCount })
            Box(Modifier.fillMaxSize().background(Color(0xFF2A2A2E))) {
                HorizontalPager(state = pagerState) { page ->
                    PdfPage(
                        file = file,
                        pageIndex = page,
                        cached = bitmaps[page],
                        onRendered = { bitmaps[page] = it }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / $pageCount",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.background(Color(0x99000000), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    file: File,
    pageIndex: Int,
    cached: Bitmap?,
    onRendered: (Bitmap) -> Unit
) {
    var bitmap by remember(pageIndex, file.absolutePath) { mutableStateOf(cached) }
    var loading by remember(pageIndex, file.absolutePath) { mutableStateOf(cached == null) }
    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.absolutePath, pageIndex) {
        if (bitmap == null) {
            loading = true
            val rendered = withContext(Dispatchers.IO) { renderPage(file, pageIndex) }
            if (rendered != null) {
                bitmap = rendered
                onRendered(rendered)
            }
            loading = false
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(pageIndex) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 4f)
                scale = newScale
                offset = if (newScale > 1f) {
                    Offset(offset.x + pan.x, offset.y + pan.y)
                } else Offset.Zero
            }
        },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF 第 ${pageIndex + 1} 页",
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offset.x, translationY = offset.y
                )
            )
            loading -> CircularProgressIndicator(color = Color.White)
            else -> Text("该页无法渲染", color = Color.White)
        }
    }
}

/** 在 IO 线程渲染单页为位图；按页面尺寸的 2 倍清晰度绘制，上限 4096。 */
private fun renderPage(file: File, pageIndex: Int): Bitmap? {
    return try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val renderScale = 2
                    val width = (page.width * renderScale).coerceAtMost(4096).coerceAtLeast(1)
                    val height = (page.height * renderScale).coerceAtMost(4096).coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}