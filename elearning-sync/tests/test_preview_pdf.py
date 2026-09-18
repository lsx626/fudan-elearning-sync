# -*- coding: utf-8 -*-
"""预览功能回归测试。

主要防止 previewer 里 PDF 预览因枚举误用（QPdfDocument.MultiPage）
而整体崩溃：该写法会抛 AttributeError，且不会被 except ImportError 捕获，
导致所有 PDF / Office 预览都只显示"预览加载失败"。
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def _run(script: str) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    env["QT_QPA_PLATFORM"] = "offscreen"
    env["PYTHONIOENCODING"] = "utf-8"
    return subprocess.run(
        [sys.executable, "-c", script],
        cwd=ROOT, env=env, capture_output=True, text=True, timeout=120,
    )


def _first_pdf() -> str:
    import glob
    pdfs = glob.glob(os.path.join(ROOT, "elearning_files", "**", "*.pdf"), recursive=True)
    assert pdfs, "测试需要至少一个 PDF 样本文件"
    return pdfs[0]


def test_pdf_preview_renders_without_attribute_error():
    """QPdfView 可用时，加载 PDF 应得到 QPdfView 而不是错误卡片。"""
    script = (
        "import glob, os\n"
        "from PySide6.QtWidgets import QApplication\n"
        "from PySide6.QtCore import QTimer\n"
        "from fudan_sync.gui.previewer import DocumentPreviewDialog, _detect_type\n"
        "pdfs = glob.glob('elearning_files/**/*.pdf', recursive=True)\n"
        "assert pdfs, 'no sample pdf'\n"
        "assert _detect_type(pdfs[0]) == 'pdf'\n"
        "app = QApplication([])\n"
        "dlg = DocumentPreviewDialog(pdfs[0])\n"
        "dlg.show()\n"
        "QTimer.singleShot(800, app.quit)\n"
        "app.exec()\n"
        "widget = dlg.preview_area.widget()\n"
        "print('WIDGET=' + type(widget).__name__)\n"
    )
    result = _run(script)
    assert result.returncode == 0, result.stderr
    assert "WIDGET=QPdfView" in result.stdout, result.stdout


def test_pdf_preview_degrades_gracefully_without_qtqpdf():
    """即使 QtPdf 不可用，_load_preview 也不应抛出未捕获异常。"""
    script = (
        "import builtins, glob\n"
        "from PySide6.QtWidgets import QApplication\n"
        "from PySide6.QtCore import QTimer\n"
        "from fudan_sync.gui import previewer\n"
        "real_import = builtins.__import__\n"
        "def fake_import(name, *a, **k):\n"
        "    if name.startswith('PySide6.QtPdf'):\n"
        "        raise ImportError('simulated: QtPdf unavailable')\n"
        "    return real_import(name, *a, **k)\n"
        "builtins.__import__ = fake_import\n"
        "pdfs = glob.glob('elearning_files/**/*.pdf', recursive=True)\n"
        "assert pdfs, 'no sample pdf'\n"
        "app = QApplication([])\n"
        "dlg = previewer.DocumentPreviewDialog(pdfs[0])\n"
        "dlg.show()\n"
        "QTimer.singleShot(400, app.quit)\n"
        "app.exec()\n"
        "print('OK_NO_CRASH')\n"
    )
    result = _run(script)
    assert result.returncode == 0, result.stderr
    assert "OK_NO_CRASH" in result.stdout