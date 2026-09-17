package edu.fudan.elearning.sync.data

/** 课程数据模型 */
data class Course(
    val id: Long,
    val name: String,
    val code: String = "",
    val term: String = "",
    val lastSyncedAt: String? = null
)

/** 文件数据模型 */
data class FileItem(
    val fileId: Long,
    val courseId: Long,
    val name: String,
    val filename: String = "",
    val folderPath: String = "",
    val localPath: String = "",
    val size: Long = 0,
    val status: String = "pending", // pending / downloaded / skipped
    val downloadedAt: String? = null,
    val url: String = ""
)

/** 同步运行记录 */
data class SyncRun(
    val id: Long = 0,
    val startedAt: String,
    val finishedAt: String? = null,
    val mode: String = "incremental",
    val filesDownloaded: Int = 0,
    val bytesDownloaded: Long = 0
)

/** 课程统计（列表页展示用） */
data class CourseStats(
    val course: Course,
    val filesTotal: Int,
    val filesDone: Int,
    val bytesDownloaded: Long
)
