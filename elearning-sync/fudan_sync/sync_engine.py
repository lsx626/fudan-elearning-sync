"""同步引擎：编排课程发现 -> 爬取 -> 增量比对 -> 下载 -> 页面归档。"""
from __future__ import annotations

import html as html_lib
import os
import re
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional

from .config import AppConfig
from .crawler import Crawler, CrawlResult, RemoteFile
from .downloader import DownloadResult, DownloadTask, Downloader
from .utils import free_space_gb
from .state import StateStore
from .utils import (format_size, is_installer_file, now_utc,
                    sanitize_path_component, unique_path)

_PAGES_SUBDIR = "_pages"
_IMG_SRC_RE = re.compile(r'src="(?!https?://|/)([^"]+)"')


@dataclass
class CourseInfo:
    id: int
    name: str
    code: str
    term: str
    enrollment_type: str
    is_favorite: bool
    raw: Dict


@dataclass
class SyncStats:
    started_at: str = field(default_factory=now_utc)
    mode: str = "incremental"
    courses: int = 0
    files_found: int = 0
    files_downloaded: int = 0
    files_skipped: int = 0
    files_locked: int = 0
    files_failed: int = 0
    files_removed: int = 0
    bytes_downloaded: int = 0
    pages_archived: int = 0
    errors: int = 0
    notes: List[str] = field(default_factory=list)

    def add(self, other: "SyncStats") -> None:
        for fld in other.__dataclass_fields__:
            value = getattr(other, fld)
            if fld in ("started_at", "mode"):
                continue
            if fld == "notes":
                # 备注是列表：合并保留，避免单门课程的问题被汇总时丢弃
                self.notes.extend(other.notes)
                continue
            if isinstance(value, (int, float)):
                setattr(self, fld, getattr(self, fld) + value)


class SyncEngine:
    def __init__(self, config: AppConfig, api, state: StateStore,
                 logger=None, progress_cb: Optional[Callable[[str, Dict], None]] = None):
        self.cfg = config
        self.api = api
        self.state = state
        self.log = logger
        self.progress_cb = progress_cb
        self.crawler = Crawler(api, logger=logger)
        self.downloader = Downloader(
            api_session=api.session,
            api_base=config.api_base,
            concurrency=config.sync.download.concurrency,
            max_retries=config.sync.download.max_retries,
            logger=logger,
        )

    def stop(self) -> None:
        """请求停止同步（给 GUI 的“停止”按钮用）。"""
        self.downloader.stop()

    def _emit(self, kind: str, payload: Dict) -> None:
        if self.progress_cb is not None:
            try:
                self.progress_cb(kind, payload)
            except Exception:  # pylint: disable=broad-except
                # 进度回调失败不能影响同步本身
                pass

    def _log(self, level: str, msg: str, *args) -> None:
        if self.log is not None:
            getattr(self.log, level)(msg, *args)

    # ------------------------------------------------------------------
    def course_local_dir(self, course: CourseInfo) -> str:
        name = sanitize_path_component(course.name or f"course_{course.id}")
        if course.code:
            code = sanitize_path_component(course.code)
            name = f"{name} [{code}]"
        return os.path.join(self.cfg.root_dir, name)

    def discover_courses(self) -> List[CourseInfo]:
        cfg = self.cfg.sync
        raw_courses = self.api.list_courses(
            enrollment_type=cfg.enrollment_type,
            only_favorites=cfg.only_favorites,
        )
        favorite_ids = set()
        if cfg.only_favorites:
            favorite_ids = set(self.api.list_favorite_ids())

        courses: List[CourseInfo] = []
        for raw in raw_courses:
            course_id = int(raw.get("id"))
            term = ((raw.get("term") or {}).get("name")) if isinstance(raw.get("term"), dict) \
                else (raw.get("term") or "")
            info = CourseInfo(
                id=course_id,
                name=raw.get("name") or raw.get("course_code") or f"课程{course_id}",
                code=raw.get("course_code") or raw.get("sis_course_id") or "",
                term=term or "",
                enrollment_type=cfg.enrollment_type,
                is_favorite=course_id in favorite_ids,
                raw=raw,
            )

            if cfg.include_courses and course_id not in cfg.include_courses:
                continue
            if course_id in cfg.exclude_courses:
                continue
            if cfg.include_terms and not any(t in info.term for t in cfg.include_terms):
                continue
            courses.append(info)

        # 持久化课程清单
        for info in courses:
            self.state.upsert_course(info.id, info.name, info.code, info.term,
                                     info.enrollment_type, info.is_favorite)
        self._log("info", "发现 %d 门课程%s", len(courses),
                  f"（仅收藏）" if cfg.only_favorites else "")
        return courses

    # ------------------------------------------------------------------
    def run(self, full: bool = False, course_ids: Optional[List[int]] = None) -> SyncStats:
        overall = SyncStats(mode="full" if full else "incremental")
        cfg = self.cfg

        courses = self.discover_courses()
        if course_ids:
            courses = [c for c in courses if c.id in set(course_ids)]
        overall.courses = len(courses)

        for idx, course in enumerate(courses, start=1):
            if self.downloader._stop.is_set():
                self._log("warning", "收到中断信号，停止后续同步")
                break
            self._emit("course_start", {"id": course.id, "name": course.name,
                                       "code": course.code, "term": course.term,
                                       "index": idx, "total": len(courses)})
            try:
                stats = self.sync_course(course, full=full)
                overall.add(stats)
                self.state.mark_course_synced(course.id)
                self._emit("course_done", {"id": course.id, "name": course.name,
                                           "files_found": stats.files_found,
                                           "files_downloaded": stats.files_downloaded,
                                           "bytes_downloaded": stats.bytes_downloaded,
                                           "pages_archived": stats.pages_archived})
            except Exception as exc:  # pylint: disable=broad-except
                overall.errors += 1
                overall.notes.append(f"课程 {course.name}: {exc}")
                self._log("error", "同步课程 %s 失败: %s", course.name, exc)

        self.state.record_run(
            started_at=overall.started_at, mode=overall.mode,
            courses=overall.courses, files_found=overall.files_found,
            files_downloaded=overall.files_downloaded,
            bytes_downloaded=overall.bytes_downloaded,
            files_failed=overall.files_failed, files_removed=overall.files_removed,
            errors=overall.errors,
            note="; ".join(overall.notes)[:500],
        )
        self._summarize(overall)
        return overall

    def _summarize(self, stats: SyncStats) -> None:
        self._log("info",
                  "同步完成 [%s]: 课程 %d | 发现文件 %d | 新增/更新 %d（%s）"
                  " | 跳过 %d | 锁定 %d | 失败 %d | 远端移除 %d | 页面归档 %d",
                  stats.mode, stats.courses, stats.files_found, stats.files_downloaded,
                  format_size(stats.bytes_downloaded), stats.files_skipped,
                  stats.files_locked, stats.files_failed, stats.files_removed,
                  stats.pages_archived)
        if stats.notes:
            for note in stats.notes[:10]:
                self._log("warning", "备注: %s", note)

    # ------------------------------------------------------------------
    def sync_course(self, course: CourseInfo, full: bool = False) -> SyncStats:
        stats = SyncStats(mode="full" if full else "incremental")
        course_dir = self.course_local_dir(course)
        os.makedirs(course_dir, exist_ok=True)

        result: CrawlResult = self.crawler.crawl_course(
            course.id, collect_pages=self.cfg.sync.archive_pages)
        stats.files_found = len(result.files)
        seen_ids = [r.file_id for r in result.files.values()]

        # 先处理远端删除：即使课程这轮一个文件都没有，只要文件列表是成功拉取的，
        # 之前已下载的文件若真的在远端被删除，也应正确标记（prune 时同步删本地）。
        # 注意必须传入本轮真实见到的 file_id 列表，否则会把全部文件误判为已删除。
        self._mark_remote_removed(course, result, stats, seen_ids=seen_ids)

        # 空课程（组织站点、未开课课程）：不留空目录
        if self.cfg.sync.skip_empty_courses and not result.files and not result.pages:
            if os.path.isdir(course_dir) and not os.listdir(course_dir):
                os.rmdir(course_dir)
            self._log("debug", "课程 [%s] 无任何文件与页面，跳过", course.name)
            return stats

        # ---- 构建下载任务 ----
        tasks: List[DownloadTask] = []
        claimed_paths: Dict[str, int] = {}  # 本地路径 -> 已占用的 file_id
        for remote in result.files.values():
            skip_reason = self._should_skip(remote)
            if skip_reason:
                if skip_reason == "locked":
                    stats.files_locked += 1
                else:
                    stats.files_skipped += 1
                self._log("debug", "跳过文件 %s（%s）", remote.filename, skip_reason)
                continue

            rel_folder = remote.folder_path or ""
            # 不同 file_id 同名同目录时改名避让，避免互相覆盖
            base_path = os.path.join(course_dir, *rel_folder.split("/"), remote.filename) \
                if rel_folder else os.path.join(course_dir, remote.filename)
            claimer = claimed_paths.get(base_path)
            if claimer is not None and claimer != remote.file_id:
                original = remote.filename
                stem, ext = os.path.splitext(remote.filename)
                idx = 1
                while os.path.join(os.path.dirname(base_path), f"{stem} ({idx}){ext}") \
                        in claimed_paths:
                    idx += 1
                remote.filename = f"{stem} ({idx}){ext}"
                base_path = os.path.join(os.path.dirname(base_path), remote.filename)
                self._log("info", "文件 %s 与 file_id %d 同名，改名为 %s",
                          original, claimer, remote.filename)
            claimed_paths[base_path] = remote.file_id

            record = {
                "file_id": remote.file_id,
                "course_id": remote.course_id,
                "filename": remote.filename,
                "display_name": remote.filename,
                "size": remote.size,
                "content_type": remote.content_type,
                "folder_id": remote.folder_id,
                "folder_path": rel_folder,
                "created_at": remote.created_at,
                "updated_at": remote.updated_at,
                "modified_at": remote.modified_at,
                "locked_for_user": remote.locked_for_user,
                "hidden": remote.hidden,
                "source": remote.source,
                "context": remote.context,
                "local_path": base_path,
                "extra": "",
            }
            needs = full or self.state.upsert_file(record)
            if needs:
                tasks.append(DownloadTask(
                    file_id=remote.file_id,
                    course_id=remote.course_id,
                    filename=remote.filename,
                    folder_path=rel_folder,
                    course_dir=course_dir,
                    size=remote.size,
                    api_path=f"/courses/{remote.course_id}/files/{remote.file_id}",
                    fallback_url=remote.url,
                ))

        # ---- 磁盘空间检查 ----
        if tasks:
            needed_gb = sum(t.size for t in tasks) / (1024 ** 3)
            free_gb = free_space_gb(self.cfg.root_dir)
            if free_gb != float("inf") and free_gb - needed_gb < \
               self.cfg.sync.download.min_free_space_gb:
                raise RuntimeError(
                    f"磁盘空间不足：需要 {needed_gb:.2f}GB，可用 {free_gb:.2f}GB")

        # ---- 执行下载 ----
        if tasks:
            self._log("info", "课程 [%s] 需下载 %d 个文件（%s）",
                      course.name, len(tasks),
                      format_size(sum(t.size for t in tasks)))
            self._emit("course_download_start", {"id": course.id, "name": course.name,
                                                "pending": len(tasks),
                                                "bytes": sum(t.size for t in tasks)})
            results: List[DownloadResult] = self.downloader.download_many(
                tasks,
                on_done=lambda r: self._on_download_done(r, stats),
            )
            for res in results:
                if res.success:
                    if not res.skipped:
                        stats.files_downloaded += 1
                        stats.bytes_downloaded += res.bytes
                    self.state.mark_downloaded(res.task.file_id, res.local_path,
                                               res.task.size)
                else:
                    stats.files_failed += 1
                    stats.errors += 1
                    self.state.mark_failed(res.task.file_id, res.error)
                    self._log("warning", "下载失败: %s -> %s", res.task.filename, res.error)
        else:
            self._log("info", "课程 [%s] 无新增/变更文件（共 %d 个文件已是最新）",
                      course.name, stats.files_found)

        # ---- 页面归档 ----
        if self.cfg.sync.archive_pages and result.pages:
            stats.pages_archived = self._archive_pages(course_dir, result.pages)

        return stats

    def _mark_remote_removed(self, course: CourseInfo, result: CrawlResult,
                             stats: SyncStats, seen_ids: Optional[List[int]]) -> None:
        """标记 / 清理远端已删除的文件。

        仅当课程文件列表成功拉取时，"本轮未见的文件 = 远端已删除"才成立；
        拉取失败（网络抖动 / 限流 / 403）时 result.files 为空，此时误判会把
        全部文件标为远端已删除，开启 prune 时甚至会删除本地已下载的文件。
        """
        if not result.files_listed_ok:
            if result.errors:
                stats.notes.append(f"课程 {course.name} 文件列表拉取失败，"
                                   f"已保留本地文件，下轮重试")
            return
        ids = seen_ids if seen_ids is not None else []
        removed = self.state.mark_missing_files(
            course.id, ids, prune=self.cfg.sync.prune)
        stats.files_removed += removed
        if removed and self.cfg.sync.prune:
            self._log("info", "课程 [%s] 远端已删除 %d 个文件，本地已同步删除",
                      course.name, removed)

    def _on_download_done(self, res: DownloadResult, stats: SyncStats) -> None:
        status = "跳过(已存在)" if res.skipped else "完成"
        self._log("info", "  [%s] %s（%s）", status,
                  os.path.basename(res.local_path or res.task.filename),
                  format_size(res.bytes or res.task.size))
        self._emit("file_done", {
            "file_id": res.task.file_id,
            "course_id": res.task.course_id,
            "filename": res.task.filename,
            "folder_path": res.task.folder_path,
            "size": res.bytes or res.task.size,
            "skipped": res.skipped,
            "success": res.success,
        })

    # ------------------------------------------------------------------
    def _should_skip(self, remote: RemoteFile) -> Optional[str]:
        """返回跳过原因字符串，None 表示需要下载。"""
        if remote.locked_for_user:
            return "locked"
        # Canvas 系统目录（课程封面图等）
        folders = [p.lower() for p in (remote.folder_path or "").split("/") if p]
        for bad in self.cfg.sync.download.exclude_folders:
            if bad and bad in folders:
                return f"系统目录 {bad}"
        ext = os.path.splitext(remote.filename)[1].lower()
        if ext in self.cfg.sync.download.exclude_extensions:
            return f"扩展名 {ext}"
        if self.cfg.sync.download.exclude_installer_files and \
                is_installer_file(remote.filename):
            return "安装包"
        limit = self.cfg.sync.download.max_file_size_mb
        if limit and remote.size > limit * 1024 * 1024:
            return f"超过大小上限 {limit}MB"
        return None

    # ------------------------------------------------------------------
    def _archive_pages(self, course_dir: str, pages) -> int:
        """把页面/作业/公告正文导出为 HTML，本地化非文件类内容。"""
        out_dir = os.path.join(course_dir, _PAGES_SUBDIR)
        os.makedirs(out_dir, exist_ok=True)
        count = 0
        for page in pages:
            try:
                title = sanitize_path_component(page.title or "untitled") or "untitled"
                kind_tag = {"page": "页面", "assignment": "作业",
                            "announcement": "公告", "syllabus": "大纲"}.get(page.kind,
                                                                            page.kind)
                filename = f"{kind_tag} - {title}.html"
                dest = unique_path(os.path.join(out_dir, filename))
                body = self._rewrite_links(page.body or "", page)
                document = (
                    "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n"
                    "<meta charset=\"utf-8\">\n"
                    f"<title>{html_lib.escape(title)}</title>\n"
                    "<meta name=\"generator\" content=\"FuXiaoXue\">\n"
                    "<style>body{font-family:sans-serif;max-width:900px;"
                    "margin:2em auto;padding:0 1em;line-height:1.6}"
                    "img{max-width:100%}table{border-collapse:collapse}"
                    "td,th{border:1px solid #ccc;padding:4px 8px}</style>\n"
                    "</head>\n<body>\n"
                    f"<h1>{html_lib.escape(title)}</h1>\n"
                    f"<p><small>类型：{kind_tag} | 归档时间：{now_utc()}</small></p>\n"
                    f"{body}\n</body>\n</html>\n"
                )
                with open(dest, "w", encoding="utf-8") as handle:
                    handle.write(document)
                count += 1
            except OSError as exc:
                self._log("warning", "归档页面失败 [%s]: %s", page.title, exc)
        return count

    def _rewrite_links(self, body: str, page) -> str:
        """把相对资源链接改为绝对路径，避免本地 HTML 无法加载资源。"""
        base = self.cfg.base_url.rstrip("/")

        def _abs_src(match):
            return f'src="{base}/{match.group(1).lstrip("/")}"'

        text = _IMG_SRC_RE.sub(_abs_src, body or "")
        # 相对链接 href（如 /courses/123/pages/xxx）
        text = re.sub(r'href="(?!https?://|mailto:|#)([^"]+)"',
                      lambda m: f'href="{base}/{m.group(1).lstrip("/")}"', text)
        return text
