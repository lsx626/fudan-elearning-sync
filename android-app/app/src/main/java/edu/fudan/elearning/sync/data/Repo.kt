package edu.fudan.elearning.sync.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

/** 数据仓库：课程/文件的增删改查与统计。 */
class Repo(context: Context) {
    private val db: DatabaseHelper = DatabaseHelper(context)

    // ---------- 课程 ----------
    fun upsertCourses(courses: List<Course>) {
        val writable = db.writableDatabase
        courses.forEach { course ->
            val values = ContentValues().apply {
                put("id", course.id)
                put("name", course.name)
                put("code", course.code)
                put("term", course.term)
            }
            val existing = writable.rawQuery(
                "SELECT last_synced_at FROM courses WHERE id=?", arrayOf(course.id.toString())
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (existing != null) {
                values.put("last_synced_at", existing)
                writable.update("courses", values, "id=?", arrayOf(course.id.toString()))
            } else {
                writable.insertWithOnConflict("courses", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun getAllCourses(): List<Course> {
        val list = mutableListOf<Course>()
        db.readableDatabase.rawQuery("SELECT * FROM courses ORDER BY name", null).use { c ->
            while (c.moveToNext()) list.add(c.toCourse())
        }
        return list
    }

    fun courseStats(): List<CourseStats> {
        val list = mutableListOf<CourseStats>()
        db.readableDatabase.rawQuery(
            """SELECT c.id, c.name, c.code, c.term, c.last_synced_at,
                      COUNT(f.file_id) AS total,
                      SUM(CASE WHEN f.status='downloaded' THEN 1 ELSE 0 END) AS done,
                      SUM(CASE WHEN f.status='downloaded' THEN COALESCE(f.size,0) ELSE 0 END) AS bytes
               FROM courses c LEFT JOIN files f ON f.course_id = c.id
               GROUP BY c.id, c.name, c.code, c.term, c.last_synced_at
               ORDER BY c.name""", null
        ).use { c ->
            while (c.moveToNext()) {
                val course = Course(
                    id = c.getLong(0), name = c.getString(1), code = c.getString(2),
                    term = c.getString(3), lastSyncedAt = c.getString(4)
                )
                list.add(
                    CourseStats(
                        course = course,
                        filesTotal = c.getInt(5),
                        filesDone = c.getInt(6),
                        bytesDownloaded = c.getLong(7)
                    )
                )
            }
        }
        return list
    }

    fun updateCourseLastSync(courseId: Long, time: String) {
        val values = ContentValues().apply { put("last_synced_at", time) }
        db.writableDatabase.update("courses", values, "id=?", arrayOf(courseId.toString()))
    }

    /** 课程名（用于按课程名重建本地目录）。 */
    fun courseName(courseId: Long): String {
        db.readableDatabase.rawQuery(
            "SELECT name FROM courses WHERE id=?", arrayOf(courseId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) ?: "" else ""
        }
    }

    // ---------- 文件 ----------
    fun upsertFile(file: FileItem) {
        val values = ContentValues().apply {
            put("file_id", file.fileId)
            put("course_id", file.courseId)
            put("name", file.name)
            put("filename", file.filename)
            put("folder_path", file.folderPath)
            put("local_path", file.localPath)
            put("size", file.size)
            put("status", file.status)
            put("downloaded_at", file.downloadedAt)
            put("url", file.url)
            put("updated_at", file.updatedAt)
        }
        db.writableDatabase.insertWithOnConflict("files", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getFile(fileId: Long): FileItem? {
        db.readableDatabase.rawQuery("SELECT * FROM files WHERE file_id=?", arrayOf(fileId.toString())).use { c ->
            return if (c.moveToFirst()) c.toFile() else null
        }
    }

    fun getFilesByCourse(courseId: Long): List<FileItem> {
        val list = mutableListOf<FileItem>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM files WHERE course_id=? ORDER BY folder_path, filename",
            arrayOf(courseId.toString())
        ).use { c -> while (c.moveToNext()) list.add(c.toFile()) }
        return list
    }

    fun getAllDownloadedFiles(): List<FileItem> {
        val list = mutableListOf<FileItem>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM files WHERE status='downloaded' ORDER BY downloaded_at DESC", null
        ).use { c -> while (c.moveToNext()) list.add(c.toFile()) }
        return list
    }

    fun deleteFile(fileId: Long) {
        db.writableDatabase.delete("files", "file_id=?", arrayOf(fileId.toString()))
    }

    fun deleteFilesByCourse(courseId: Long) {
        db.writableDatabase.delete("files", "course_id=?", arrayOf(courseId.toString()))
    }

    /**
     * 把本轮「课程文件列表成功但未出现」的远端文件标记为 remote_missing。
     *
     * 只改状态、**不删除本地文件**：调用方必须先用列表成功这一闸门筛选过
     * （见 `SyncPolicy.shouldMarkRemoteMissing`），网络/权限/解析失败时绝不能调用。
     */
    fun markRemoteMissing(fileIds: List<Long>): Int {
        if (fileIds.isEmpty()) return 0
        val dbw = db.writableDatabase
        var changed = 0
        dbw.beginTransaction()
        try {
            fileIds.forEach { id ->
                val values = ContentValues().apply { put("status", "remote_missing") }
                changed += dbw.update("files", values, "file_id=?", arrayOf(id.toString()))
            }
            dbw.setTransactionSuccessful()
        } finally {
            dbw.endTransaction()
        }
        return changed
    }

    /** 标记单个文件下载失败（保留既有 local_path 与断点，便于下次重试）。 */
    fun markFailed(fileId: Long) {
        val values = ContentValues().apply { put("status", "failed") }
        db.writableDatabase.update("files", values, "file_id=?", arrayOf(fileId.toString()))
    }

    /** 状态计数（用于同步结果与界面提示）。 */
    fun countByStatus(): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        db.readableDatabase.rawQuery(
            "SELECT status, COUNT(*) FROM files GROUP BY status", null
        ).use { c ->
            while (c.moveToNext()) out[c.getString(0) ?: ""] = c.getInt(1)
        }
        return out
    }

    // ---------- 同步记录 ----------
    fun recordRun(run: SyncRun): Long {
        val values = ContentValues().apply {
            put("started_at", run.startedAt)
            put("finished_at", run.finishedAt)
            put("mode", run.mode)
            put("files_downloaded", run.filesDownloaded)
            put("bytes_downloaded", run.bytesDownloaded)
            put("files_failed", run.filesFailed)
            put("error", run.error)
        }
        return db.writableDatabase.insert("sync_runs", null, values)
    }

    /** 最近一次同步记录（用于界面显示上次结果与失败原因）。 */
    fun lastRun(): SyncRun? {
        db.readableDatabase.rawQuery(
            "SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1", null
        ).use { c ->
            return if (c.moveToFirst()) {
                SyncRun(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    startedAt = c.getString(c.getColumnIndexOrThrow("started_at")),
                    finishedAt = c.getString(c.getColumnIndexOrThrow("finished_at")),
                    mode = c.getString(c.getColumnIndexOrThrow("mode")),
                    filesDownloaded = c.getInt(c.getColumnIndexOrThrow("files_downloaded")),
                    bytesDownloaded = c.getLong(c.getColumnIndexOrThrow("bytes_downloaded")),
                    filesFailed = runCatching {
                        c.getInt(c.getColumnIndexOrThrow("files_failed"))
                    }.getOrDefault(0),
                    error = runCatching {
                        c.getString(c.getColumnIndexOrThrow("error")) ?: ""
                    }.getOrDefault("")
                )
            } else null
        }
    }

    // ---------- 统计 ----------
    fun totalStats(): Triple<Int, Int, Long> {
        db.readableDatabase.rawQuery(
            """SELECT COUNT(*),
                      SUM(CASE WHEN status='downloaded' THEN 1 ELSE 0 END),
                      SUM(CASE WHEN status='downloaded' THEN COALESCE(size,0) ELSE 0 END)
               FROM files""", null
        ).use { c ->
            return if (c.moveToFirst())
                Triple(c.getInt(0), c.getInt(1), c.getLong(2))
            else Triple(0, 0, 0L)
        }
    }

    // ---------- 学期列表 ----------
    fun distinctTerms(): List<String> {
        val list = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            "SELECT DISTINCT term FROM courses WHERE term != '' ORDER BY term", null
        ).use { c -> while (c.moveToNext()) list.add(c.getString(0)) }
        return list
    }

    fun close() {
        db.close()
    }
}

private fun Cursor.toCourse(): Course = Course(
    id = getLong(getColumnIndexOrThrow("id")),
    name = getString(getColumnIndexOrThrow("name")),
    code = getString(getColumnIndexOrThrow("code")),
    term = getString(getColumnIndexOrThrow("term")),
    lastSyncedAt = getString(getColumnIndexOrThrow("last_synced_at"))
)

private fun Cursor.toFile(): FileItem = FileItem(
    fileId = getLong(getColumnIndexOrThrow("file_id")),
    courseId = getLong(getColumnIndexOrThrow("course_id")),
    name = getString(getColumnIndexOrThrow("name")),
    filename = getString(getColumnIndexOrThrow("filename")),
    folderPath = getString(getColumnIndexOrThrow("folder_path")),
    localPath = getString(getColumnIndexOrThrow("local_path")),
    size = getLong(getColumnIndexOrThrow("size")),
    status = getString(getColumnIndexOrThrow("status")),
    downloadedAt = getString(getColumnIndexOrThrow("downloaded_at")),
    url = getString(getColumnIndexOrThrow("url")),
    updatedAt = runCatching { getString(getColumnIndexOrThrow("updated_at")) }.getOrDefault("") ?: ""
)
