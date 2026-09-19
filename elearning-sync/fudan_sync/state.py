"""SQLite 状态存储：记录课程、文件与同步元数据，支撑增量同步。"""
from __future__ import annotations

import json
import os
import sqlite3
import threading
from typing import Any, Dict, List, Optional

from .utils import now_utc


class StateStore:
    """线程安全的 SQLite 状态库（WAL 模式）。"""

    def __init__(self, db_path: str, logger=None):
        self.db_path = db_path
        self.log = logger
        self._local = threading.local()
        os.makedirs(os.path.dirname(os.path.abspath(db_path)), exist_ok=True)
        self._init_db()

    # ------------------------------------------------------------------
    @property
    def conn(self) -> sqlite3.Connection:
        """每个线程一个连接（sqlite3 连接不可跨线程共享）。"""
        if not hasattr(self._local, "conn"):
            conn = sqlite3.connect(self.db_path, timeout=30.0)
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA synchronous=NORMAL")
            self._local.conn = conn
        return self._local.conn

    def _init_db(self) -> None:
        with self._write_lock_cursor() as cur:
            cur.executescript("""
            CREATE TABLE IF NOT EXISTS courses (
                id INTEGER PRIMARY KEY,
                name TEXT,
                code TEXT,
                term TEXT,
                enrollment_type TEXT,
                is_favorite INTEGER DEFAULT 0,
                first_seen_at TEXT,
                last_synced_at TEXT
            );

            CREATE TABLE IF NOT EXISTS files (
                file_id INTEGER PRIMARY KEY,
                course_id INTEGER NOT NULL,
                filename TEXT NOT NULL,
                display_name TEXT,
                size INTEGER DEFAULT 0,
                content_type TEXT,
                folder_id INTEGER,
                folder_path TEXT DEFAULT '',
                created_at TEXT,
                updated_at TEXT,
                modified_at TEXT,
                locked_for_user INTEGER DEFAULT 0,
                hidden INTEGER DEFAULT 0,
                source TEXT DEFAULT '',
                context TEXT DEFAULT '',
                local_path TEXT,
                status TEXT DEFAULT 'pending',   -- pending|downloaded|failed|remote_missing
                last_seen_at TEXT,
                downloaded_at TEXT,
                last_error TEXT,
                extra TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_files_course ON files(course_id);
            CREATE INDEX IF NOT EXISTS idx_files_status ON files(status);
            CREATE INDEX IF NOT EXISTS idx_files_path ON files(local_path);

            CREATE TABLE IF NOT EXISTS sync_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at TEXT,
                finished_at TEXT,
                mode TEXT,
                courses INTEGER DEFAULT 0,
                files_found INTEGER DEFAULT 0,
                files_downloaded INTEGER DEFAULT 0,
                bytes_downloaded INTEGER DEFAULT 0,
                files_failed INTEGER DEFAULT 0,
                files_removed INTEGER DEFAULT 0,
                errors INTEGER DEFAULT 0,
                note TEXT
            );

            CREATE TABLE IF NOT EXISTS kv (
                key TEXT PRIMARY KEY,
                value TEXT
            );
            """)

    _write_lock = threading.Lock()

    def _write_lock_cursor(self):
        class _Ctx:
            def __init__(self, store):
                self.store = store
            def __enter__(self):
                self.store._write_lock.acquire()
                self.cur = self.store.conn.cursor()
                return self.cur
            def __exit__(self, exc_type, exc, tb):
                try:
                    # 异常路径必须回滚：否则多语句写入中途失败会留下半成品，
                    # 甚至把「已写入的文件状态」提交上去。
                    if exc_type is None:
                        self.store.conn.commit()
                    else:
                        self.store.conn.rollback()
                finally:
                    self.cur.close()
                    self.store._write_lock.release()
                return False
        return _Ctx(self)

    # ------------------------------------------------------------------
    # 课程
    # ------------------------------------------------------------------
    def upsert_course(self, course_id: int, name: str, code: str, term: str,
                      enrollment_type: str, is_favorite: bool) -> None:
        with self._write_lock_cursor() as cur:
            cur.execute(
                """INSERT INTO courses (id, name, code, term, enrollment_type,
                                        is_favorite, first_seen_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(id) DO UPDATE SET
                       name=excluded.name, code=excluded.code, term=excluded.term,
                       enrollment_type=excluded.enrollment_type,
                       is_favorite=excluded.is_favorite""",
                (course_id, name, code, term, enrollment_type, int(is_favorite), now_utc()),
            )

    def mark_course_synced(self, course_id: int) -> None:
        with self._write_lock_cursor() as cur:
            cur.execute("UPDATE courses SET last_synced_at=? WHERE id=?",
                        (now_utc(), course_id))

    def list_courses(self) -> List[Dict[str, Any]]:
        cur = self.conn.execute("SELECT * FROM courses ORDER BY name")
        return [dict(r) for r in cur.fetchall()]

    def course_progress(self) -> List[Dict[str, Any]]:
        """课程维度汇总（界面表格用）：文件数 / 已下载数 / 本地容量 / 最近同步。"""
        cur = self.conn.execute(
            """SELECT c.id, c.name, c.code, c.term, c.last_synced_at,
                      COUNT(f.file_id) AS files_total,
                      SUM(CASE WHEN f.status='downloaded' THEN 1 ELSE 0 END) AS files_done,
                      SUM(CASE WHEN f.status='downloaded' THEN COALESCE(f.size, 0)
                               ELSE 0 END) AS bytes_downloaded
               FROM courses c LEFT JOIN files f ON f.course_id = c.id
               GROUP BY c.id, c.name, c.code, c.term, c.last_synced_at
               ORDER BY c.name""")
        return [dict(r) for r in cur.fetchall()]

    # ------------------------------------------------------------------
    # 文件
    # ------------------------------------------------------------------
    def get_file(self, file_id: int) -> Optional[Dict[str, Any]]:
        cur = self.conn.execute("SELECT * FROM files WHERE file_id=?", (file_id,))
        row = cur.fetchone()
        return dict(row) if row else None

    def upsert_file(self, remote: Dict[str, Any]) -> bool:
        """写入远端文件信息；返回 True 表示本地副本需要（重新）下载。

        判定条件：本地不存在 / 状态非 downloaded / 大小或时间戳变化 / 路径变化。
        """
        needs_download = False
        with self._write_lock_cursor() as cur:
            cur.execute("SELECT * FROM files WHERE file_id=?", (remote["file_id"],))
            row = cur.fetchone()

            if row is None:
                needs_download = True
            else:
                stored = dict(row)
                if stored.get("status") != "downloaded":
                    needs_download = True
                elif (int(stored.get("size") or -1) != int(remote.get("size") or 0)
                      or stored.get("updated_at") != remote.get("updated_at")
                      or stored.get("modified_at") != remote.get("modified_at")
                      or stored.get("filename") != remote.get("filename")
                      or stored.get("local_path") != remote.get("local_path")):
                    needs_download = True
                # 本地文件丢失也需重下
                local_path = stored.get("local_path")
                if not local_path or not os.path.exists(local_path):
                    needs_download = True

            cur.execute(
                """INSERT INTO files (file_id, course_id, filename, display_name, size,
                                      content_type, folder_id, folder_path, created_at,
                                      updated_at, modified_at, locked_for_user, hidden,
                                      source, context, local_path, status, last_seen_at,
                                      last_error, extra)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                           COALESCE((SELECT status FROM files WHERE file_id=?), 'pending'),
                           ?, NULL,
                           COALESCE((SELECT extra FROM files WHERE file_id=?), ?))
                   ON CONFLICT(file_id) DO UPDATE SET
                       course_id=excluded.course_id, filename=excluded.filename,
                       display_name=excluded.display_name, size=excluded.size,
                       content_type=excluded.content_type, folder_id=excluded.folder_id,
                       folder_path=excluded.folder_path, created_at=excluded.created_at,
                       updated_at=excluded.updated_at, modified_at=excluded.modified_at,
                       locked_for_user=excluded.locked_for_user, hidden=excluded.hidden,
                       source=excluded.source, context=excluded.context,
                       local_path=excluded.local_path, last_seen_at=excluded.last_seen_at""",
                (remote["file_id"], remote["course_id"], remote["filename"],
                 remote.get("display_name"), remote.get("size") or 0,
                 remote.get("content_type", ""), remote.get("folder_id"),
                 remote.get("folder_path", ""), remote.get("created_at"),
                 remote.get("updated_at"), remote.get("modified_at"),
                 int(bool(remote.get("locked_for_user"))), int(bool(remote.get("hidden"))),
                 remote.get("source", ""), remote.get("context", ""),
                 remote.get("local_path"), remote["file_id"], now_utc(),
                 remote["file_id"], remote.get("extra")),
            )
        return needs_download

    def set_local_path(self, file_id: int, local_path: str) -> None:
        with self._write_lock_cursor() as cur:
            cur.execute("UPDATE files SET local_path=? WHERE file_id=?",
                        (local_path, file_id))

    def mark_downloaded(self, file_id: int, local_path: str, size: int) -> None:
        with self._write_lock_cursor() as cur:
            cur.execute(
                """UPDATE files SET status='downloaded', local_path=?, downloaded_at=?,
                                   last_error=NULL WHERE file_id=?""",
                (local_path, now_utc(), file_id))

    def mark_failed(self, file_id: int, error: str) -> None:
        with self._write_lock_cursor() as cur:
            cur.execute("UPDATE files SET status='failed', last_error=? WHERE file_id=?",
                        (error[:500], file_id))

    @staticmethod
    def _is_safe_prune_path(path: str, prune_root: str) -> bool:
        """只允许清理指定课程目录内的文件，兼容旧库中的异常路径。"""
        try:
            root = os.path.realpath(os.path.abspath(prune_root))
            candidate = os.path.realpath(os.path.abspath(path))
            common = os.path.commonpath((root, candidate))
        except (OSError, ValueError):
            return False
        return (os.path.normcase(common) == os.path.normcase(root)
                and os.path.normcase(candidate) != os.path.normcase(root))

    def mark_missing_files(self, course_id: int, seen_file_ids: List[int],
                           prune: bool = False,
                           prune_root: Optional[str] = None) -> int:
        """标记远端已删除文件；只清理明确限定在 prune_root 内的副本。"""
        removed = 0
        placeholders = ",".join("?" * len(seen_file_ids)) if seen_file_ids else "0"
        cur = self.conn.execute(
            f"""SELECT file_id, local_path FROM files
                WHERE course_id=? AND status != 'remote_missing'
                  AND file_id NOT IN ({placeholders})""",
            (course_id, *seen_file_ids),
        )
        rows = [dict(r) for r in cur.fetchall()]
        if not rows:
            return 0
        with self._write_lock_cursor() as wcur:
            for row in rows:
                wcur.execute(
                    "UPDATE files SET status='remote_missing' WHERE file_id=?",
                    (row["file_id"],))
                removed += 1
        if prune:
            for row in rows:
                path = row.get("local_path")
                if path and os.path.exists(path) and prune_root and \
                        self._is_safe_prune_path(path, prune_root):
                    try:
                        os.remove(path)
                    except OSError as exc:
                        if self.log:
                            self.log.warning("删除本地文件失败 %s: %s", path, exc)
                elif path and os.path.exists(path) and self.log:
                    self.log.warning("跳过课程目录外的本地文件清理: %s", path)
        return removed

    def list_files_by_course(self, course_id: int) -> List[Dict[str, Any]]:
        cur = self.conn.execute(
            "SELECT * FROM files WHERE course_id=? ORDER BY folder_path, filename",
            (course_id,))
        return [dict(r) for r in cur.fetchall()]

    # ------------------------------------------------------------------
    def record_run(self, **stats) -> int:
        with self._write_lock_cursor() as cur:
            cur.execute(
                """INSERT INTO sync_runs (started_at, finished_at, mode, courses,
                                          files_found, files_downloaded,
                                          bytes_downloaded, files_failed, files_removed,
                                          errors, note)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (stats.get("started_at"), now_utc(), stats.get("mode", "incremental"),
                 stats.get("courses", 0), stats.get("files_found", 0),
                 stats.get("files_downloaded", 0), stats.get("bytes_downloaded", 0),
                 stats.get("files_failed", 0), stats.get("files_removed", 0),
                 stats.get("errors", 0), stats.get("note", "")),
            )
            return cur.lastrowid or 0

    def last_run(self) -> Optional[Dict[str, Any]]:
        cur = self.conn.execute(
            "SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1")
        row = cur.fetchone()
        return dict(row) if row else None

    def stats(self) -> Dict[str, Any]:
        cur = self.conn.execute(
            """SELECT
                 (SELECT COUNT(*) FROM courses) AS courses,
                 (SELECT COUNT(*) FROM files) AS files_total,
                 (SELECT COUNT(*) FROM files WHERE status='downloaded') AS files_downloaded,
                 (SELECT COUNT(*) FROM files WHERE status='pending') AS files_pending,
                 (SELECT COUNT(*) FROM files WHERE status='failed') AS files_failed,
                 (SELECT COUNT(*) FROM files WHERE status='remote_missing') AS files_missing,
                 (SELECT COALESCE(SUM(size), 0) FROM files WHERE status='downloaded') AS bytes""")
        return dict(cur.fetchone())

    def set_kv(self, key: str, value: Any) -> None:
        with self._write_lock_cursor() as cur:
            cur.execute(
                "INSERT INTO kv (key, value) VALUES (?, ?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (key, json.dumps(value, ensure_ascii=False)))

    def get_kv(self, key: str, default: Any = None) -> Any:
        cur = self.conn.execute("SELECT value FROM kv WHERE key=?", (key,))
        row = cur.fetchone()
        if row is None:
            return default
        try:
            return json.loads(row["value"])
        except (ValueError, TypeError):
            return row["value"]

    def close(self) -> None:
        if hasattr(self._local, "conn"):
            self._local.conn.close()
            del self._local.conn
