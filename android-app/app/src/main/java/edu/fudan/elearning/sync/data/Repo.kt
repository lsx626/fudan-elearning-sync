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

    // ---------- 同步记录 ----------
    fun recordRun(run: SyncRun): Long {
        val values = ContentValues().apply {
            put("started_at", run.startedAt)
            put("finished_at", run.finishedAt)
            put("mode", run.mode)
            put("files_downloaded", run.filesDownloaded)
            put("bytes_downloaded", run.bytesDownloaded)
        }
        return db.writableDatabase.insert("sync_runs", null, values)
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
    url = getString(getColumnIndexOrThrow("url"))
)
