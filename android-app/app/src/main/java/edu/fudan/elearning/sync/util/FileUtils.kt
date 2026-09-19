package edu.fudan.elearning.sync.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import edu.fudan.elearning.sync.data.FileItem
import java.io.File

/** 文件操作工具：分享与通用辅助。应用内预览由 PreviewScreen 统一路由。 */
object FileUtils {

    fun fileUri(context: Context, path: String): android.net.Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path)
        )

    /** 分享文件（系统分享面板）：只授予临时只读 URI 权限。 */
    fun shareFile(context: Context, file: FileItem) {
        if (file.localPath.isEmpty()) {
            toast(context, "文件尚未下载，请先同步")
            return
        }
        try {
            val local = File(file.localPath)
            if (!local.exists()) {
                toast(context, "本地文件不存在，可能已被删除")
                return
            }
            val uri = fileUri(context, file.localPath)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = guessMime(file.filename)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // ApplicationContext 启动 Activity 必须加 NEW_TASK，否则抛
            // "Calling startActivity() from outside of an Activity context"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(intent, "分享文件")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            toast(context, "没有可处理此文件的应用")
        } catch (e: Exception) {
            toast(context, "无法分享此文件：${e.message ?: "未知错误"}")
        }
    }

    /** 分享本地文件（系统分享面板）：只授予临时只读 URI 权限。 */
    fun shareFile(context: Context, file: File) {
        if (!file.exists()) {
            toast(context, "本地文件不存在，可能已被删除")
            return
        }
        try {
            val uri = fileUri(context, file.absolutePath)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = guessMime(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(intent, "分享文件")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            toast(context, "没有可处理此文件的应用")
        } catch (e: Exception) {
            toast(context, "无法分享此文件：${e.message ?: "未知错误"}")
        }
    }
    /** 文件同步状态的中文描述。 */
    fun statusText(status: String): String = when (status) {
        "downloaded" -> "已下载"
        "pending" -> "待下载"
        "skipped" -> "已跳过"
        "remote_missing" -> "远端已删除"
        "failed" -> "下载失败"
        else -> status
    }

    /** 短暂提示。 */
    private fun toast(context: Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 依据扩展名推断 MIME 类型。 */
    fun guessMime(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "txt", "md" -> "text/plain"
            "csv" -> "text/csv"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            else -> "*/*"
        }
    }

    /** 格式化字节大小。 */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}