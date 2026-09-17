"""同步引擎数据安全回归测试。

核心断言：课程文件列表拉取失败时，绝不能把本地已下载的文件误判为
"远端已删除"（开启 prune 时会被真正删除，造成用户数据丢失）。
运行：python -m pytest elearning-sync/tests/test_sync_safety.py
    或 python elearning-sync/tests/test_sync_safety.py
"""
from __future__ import annotations

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from fudan_sync.config import AppConfig  # noqa: E402
from fudan_sync.state import StateStore  # noqa: E402
from fudan_sync.sync_engine import CourseInfo, SyncEngine  # noqa: E402

FOLDER_ID = 77


class FakeAPI:
    """假 Canvas API：files 接口可按需失败 / 返回空 / 返回一个文件。"""

    def __init__(self, mode: str = "fail"):
        self.mode = mode  # fail | empty | one_file
        self.session = type("S", (), {})()

    def list_courses(self, **kwargs):
        return [{"id": 1, "name": "数学", "course_code": "MATH",
                 "term": {"name": "2025"}}]

    def get_course(self, course_id):
        return {"syllabus_body": ""}

    def iter_pages(self, path, params=None, max_pages=None):
        if "/files" in path:
            if self.mode == "fail":
                raise RuntimeError("模拟 503 网络故障")
            if self.mode == "one_file":
                return iter([{"id": 10, "display_name": "a.pdf", "filename": "a.pdf",
                              "size": 100, "folder_id": FOLDER_ID,
                              "updated_at": "t1", "url": "https://invalid/a.pdf"}])
            return iter([])  # empty：文件确实已被远端删除
        if "/folders" in path:
            # 一个根目录 + 其下的"课件"子目录，使 folder_path 解析为 "课件"
            return iter([
                {"id": 1, "name": "course files", "parent_folder_id": None},
                {"id": FOLDER_ID, "name": "课件", "parent_folder_id": 1},
            ])
        return iter([])

    def get(self, path, params=None):
        return {}


def _make_env(prune: bool = False):
    tmp = tempfile.mkdtemp()
    cfg = AppConfig(root_dir=os.path.join(tmp, "files"),
                    state_db=os.path.join(tmp, "st.db"))
    cfg.sync.prune = prune
    os.makedirs(cfg.root_dir, exist_ok=True)
    state = StateStore(cfg.state_db)
    state.upsert_course(1, "数学", "MATH", "2025", "student", False)
    local = os.path.join(cfg.root_dir, "数学 [MATH]", "课件", "a.pdf")
    os.makedirs(os.path.dirname(local), exist_ok=True)
    with open(local, "wb") as handle:
        handle.write(b"x" * 100)
    state.upsert_file({"file_id": 10, "course_id": 1, "filename": "a.pdf",
                       "size": 100, "updated_at": "t1", "local_path": local,
                       "folder_path": "课件"})
    state.mark_downloaded(10, local, 100)
    course = CourseInfo(1, "数学", "MATH", "2025", "student", False, {})
    return cfg, state, local, course


class SyncSafetyTests(unittest.TestCase):
    """拉取失败时保留本地文件；真正删除时正确检测；正常同步不误删。"""

    def test_crawl_failure_preserves_files(self):
        """场景 A：文件列表拉取失败 -> 不得标记远端删除，不得删本地文件。"""
        for prune in (False, True):
            cfg, state, local, course = _make_env(prune=prune)
            engine = SyncEngine(cfg, FakeAPI("fail"), state)
            stats = engine.sync_course(course)
            after = state.stats()
            state.close()
            self.assertEqual(after["files_missing"], 0, "不应标记为远端已删除")
            self.assertTrue(os.path.exists(local), "本地文件必须保留")
            self.assertEqual(stats.files_removed, 0)

    def test_real_removal_is_detected(self):
        """场景 B：列表成功且远端确实无此文件 -> 正确标记为远端已删除。"""
        cfg, state, local, course = _make_env(prune=False)
        engine = SyncEngine(cfg, FakeAPI("empty"), state)
        engine.sync_course(course)
        after = state.stats()
        state.close()
        self.assertEqual(after["files_missing"], 1)
        self.assertTrue(os.path.exists(local), "prune 关闭时本地文件保留")

    def test_prune_deletes_actually_removed(self):
        """场景 C：开启 prune 且远端已删除 -> 本地文件同步删除。"""
        cfg, state, local, course = _make_env(prune=True)
        engine = SyncEngine(cfg, FakeAPI("empty"), state)
        stats = engine.sync_course(course)
        state.close()
        self.assertEqual(stats.files_removed, 1)
        self.assertFalse(os.path.exists(local), "prune 开启时应删除本地文件")

    def test_normal_sync_keeps_files(self):
        """场景 D：远端文件仍在 -> 即使重下载失败，本地文件也不得被误删。"""
        for prune in (False, True):
            cfg, state, local, course = _make_env(prune=prune)
            engine = SyncEngine(cfg, FakeAPI("one_file"), state)
            stats = engine.sync_course(course)
            after = state.stats()
            state.close()
            self.assertTrue(os.path.exists(local), "远端仍存在的文件不得被误删")
            self.assertEqual(stats.files_removed, 0)
            self.assertEqual(after["files_missing"], 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
