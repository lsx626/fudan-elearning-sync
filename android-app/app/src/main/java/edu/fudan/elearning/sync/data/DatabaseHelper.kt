package edu.fudan.elearning.sync.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本地 SQLite 数据库：课程与文件状态。 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "fudan_sync.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE courses (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                code TEXT DEFAULT '',
                term TEXT DEFAULT '',
                last_synced_at TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE files (
                file_id INTEGER PRIMARY KEY,
                course_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                filename TEXT DEFAULT '',
                folder_path TEXT DEFAULT '',
                local_path TEXT DEFAULT '',
                size INTEGER DEFAULT 0,
                status TEXT DEFAULT 'pending',
                downloaded_at TEXT,
                url TEXT DEFAULT '',
                FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """CREATE TABLE sync_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at TEXT NOT NULL,
                finished_at TEXT,
                mode TEXT DEFAULT 'incremental',
                files_downloaded INTEGER DEFAULT 0,
                bytes_downloaded INTEGER DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS files")
        db.execSQL("DROP TABLE IF EXISTS courses")
        db.execSQL("DROP TABLE IF EXISTS sync_runs")
        onCreate(db)
    }
}
