package edu.fudan.elearning.sync.sync

import android.content.Context
import edu.fudan.elearning.sync.data.Course
import edu.fudan.elearning.sync.data.FileItem
import edu.fudan.elearning.sync.data.Repo
import edu.fudan.elearning.sync.network.CanvasApi
import edu.fudan.elearning.sync.network.CanvasFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 同步结果。 */
data class SyncResult(
    val coursesCount: Int = 0,
    val filesTotal: Int = 0,
    val filesDownloaded: Int = 0,
    val bytesDownloaded: Long = 0,
    val filesFailed: Int = 0
)

/** 安装包等"驳杂"文件扩展名过滤。 */
private val INSTALLER_EXTENSIONS = setOf(
    "exe", "msi", "dmg", "pkg", "apk", "deb", "rpm", "jar", "iso", "img",
    "bin", "dll", "sys", "cmd", "bat", "com", "scr", "7z", "rar", "tar"
)

/** 同步引擎：课程发现 → 文件增量比对 → 下载 → 落库。 */
class SyncEngine(
    private val context: Context,
    private val api: CanvasApi,
    private val repo: Repo
) {
    private val downloader = DownloadManager(context)

    /** 执行一次同步。返回结果统计。 */
    suspend fun sync(
        full: Boolean = false,
        onProgress: (phase: String, done: Int, total: Int, message: String) -> Unit = { _, _, _, _ -> }
    ): SyncResult {
        val startedAt = now()
        var filesDownloaded = 0
        var bytesDownloaded = 0L
        var filesFailed = 0

        // 1. 拉取课程列表
        onProgress("courses", 0, 0, "正在获取课程列表…")
        val courses = api.getCourses()
        val validCourses = courses.filter { it.name.isNotBlank() }
        repo.upsertCourses(validCourses.map {
            Course(
                id = it.id,
                name = it.name,
                code = it.courseCode,
                term = it.term?.name ?: ""
            )
        })
        onProgress("courses", validCourses.size, validCourses.size, "共 ${validCourses.size} 门课程")

        var filesTotal = 0

        // 2. 遍历课程同步文件
        validCourses.forEachIndexed { index, canvasCourse ->
            onProgress(
                "course", index + 1, validCourses.size,
                "同步课程：${canvasCourse.name}"
            )

            val files = api.getCourseFiles(canvasCourse.id)
            // 空课程（组织站点）跳过
            if (files.isEmpty()) {
                repo.updateCourseLastSync(canvasCourse.id, startedAt)
                return@forEachIndexed
            }

            val courseDir = downloader.courseDir(canvasCourse.name)
            files.forEach { canvasFile ->
                filesTotal++
                // 过滤"驳杂"内容
                if (shouldSkip(canvasFile)) {
                    return@forEach
                }

                val existing = repo.getFile(canvasFile.id)
                // 增量：已下载且大小未变则跳过（省流量）
                if (!full && existing != null && existing.status == "downloaded" &&
                    existing.size == canvasFile.size
                ) {
                    return@forEach
                }

                val filename = DownloadManager.sanitize(
                    canvasFile.filename.ifEmpty { canvasFile.displayName.ifEmpty { canvasFile.id.toString() } }
                )
                val dest = File(courseDir, filename)
                val ok = downloader.download(canvasFile.url, dest)
                if (ok) {
                    filesDownloaded++
                    bytesDownloaded += canvasFile.size
                    repo.upsertFile(
                        FileItem(
                            fileId = canvasFile.id,
                            courseId = canvasCourse.id,
                            name = canvasFile.displayName.ifEmpty { filename },
                            filename = filename,
                            folderPath = courseDir.absolutePath,
                            localPath = dest.absolutePath,
                            size = canvasFile.size,
                            status = "downloaded",
                            downloadedAt = now(),
                            url = canvasFile.url
                        )
                    )
                    onProgress("file", filesDownloaded, 0, "下载：$filename")
                } else {
                    filesFailed++
                }
            }

            repo.updateCourseLastSync(canvasCourse.id, startedAt)
        }

        val result = SyncResult(
            coursesCount = validCourses.size,
            filesTotal = filesTotal,
            filesDownloaded = filesDownloaded,
            bytesDownloaded = bytesDownloaded,
            filesFailed = filesFailed
        )
        repo.recordRun(
            edu.fudan.elearning.sync.data.SyncRun(
                startedAt = startedAt,
                finishedAt = now(),
                mode = if (full) "full" else "incremental",
                filesDownloaded = filesDownloaded,
                bytesDownloaded = bytesDownloaded
            )
        )
        onProgress("done", filesDownloaded, filesTotal, "同步完成")
        return result
    }

    /** 判断是否应跳过"驳杂"文件。 */
    private fun shouldSkip(file: CanvasFile): Boolean {
        val name = file.filename.ifEmpty { file.displayName }
        val lower = name.lowercase(Locale.ROOT)
        // 安装包 / 可执行文件
        val ext = lower.substringAfterLast('.', "")
        if (ext in INSTALLER_EXTENSIONS) return true
        // Canvas 系统封面图目录
        if (lower.contains("course_image")) return true
        return false
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}
