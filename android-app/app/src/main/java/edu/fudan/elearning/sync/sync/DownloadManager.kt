package edu.fudan.elearning.sync.sync

import android.content.Context
import edu.fudan.elearning.sync.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 文件下载管理器：负责把 Canvas 文件下载到应用外部文件目录。 */
class DownloadManager(private val context: Context) {

    private val rootDir: File
        get() = File(context.getExternalFilesDir(null), "elearning").apply { mkdirs() }

    fun rootPath(): String = rootDir.absolutePath

    /** 计算某个课程目录下的文件绝对路径。 */
    fun courseDir(courseName: String): File =
        File(rootDir, sanitize(courseName)).apply { mkdirs() }

    /** 下载文件到指定目标，返回是否成功。 */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.download(url)
            resp.use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** 净化文件名，移除非法字符。 */
        fun sanitize(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().ifEmpty { "未命名" }
    }
}
