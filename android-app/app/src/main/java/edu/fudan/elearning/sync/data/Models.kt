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
    val status: String = "pending", // pending / downloaded / failed / remote_missing
    val downloadedAt: String? = null,
    val url: String = "",
    /** 远端 `updated_at`；增量同步据此判断「同大小但内容已更新」。 */
    val updatedAt: String = ""
)

/** 同步运行记录 */
data class SyncRun(
    val id: Long = 0,
    val startedAt: String,
    val finishedAt: String? = null,
    val mode: String = "incremental",
    val filesDownloaded: Int = 0,
    val bytesDownloaded: Long = 0,
    val filesFailed: Int = 0,
    /** 失败原因（成功时为空）。用于历史记录与排障，不含凭据。 */
    val error: String = ""
)

/** 课程统计（列表页展示用） */
data class CourseStats(
    val course: Course,
    val filesTotal: Int,
    val filesDone: Int,
    val bytesDownloaded: Long
)
