# -*- coding: utf-8 -*-
"""文档预览对话框：纯本地渲染，支持 PDF / 图片 / 文本 / Office 文档。

根据文件扩展名自动选择预览方式，Office 文档优先尝试 pywin32 转 PDF，
不可用时展示文件信息卡片并提供"用默认应用打开"按钮。
"""
from __future__ import annotations

import os
import shutil
import tempfile
from typing import Optional

from PySide6.QtCore import QSize, Qt, QTimer
from PySide6.QtGui import QGuiApplication, QPixmap
from PySide6.QtWidgets import (QDialog, QFileDialog, QFrame, QHBoxLayout,
                               QLabel, QMessageBox, QPlainTextEdit,
                               QPushButton, QScrollArea, QSizePolicy,
                               QVBoxLayout, QWidget)

from ..utils import format_size
from .icon import app_icon
from .styles import ACCENT, BG, BORDER, CARD, TEXT, TEXT_SECONDARY

# ---------------------------------------------------------------------------
# 文件类型分类
# ---------------------------------------------------------------------------

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".ico",
              ".svg", ".tif", ".tiff"}
TEXT_EXTS = {".txt", ".md", ".rst", ".log", ".csv", ".json", ".xml",
             ".html", ".htm", ".css", ".js", ".py", ".java", ".c", ".cpp",
             ".h", ".hpp", ".cs", ".go", ".rs", ".ts", ".tsx", ".jsx",
             ".sh", ".bat", ".ps1", ".yaml", ".yml", ".toml", ".ini",
             ".conf", ".cfg", ".sql", ".r", ".m", ".php", ".rb", ".swift",
             ".kt", ".dart", ".vue", ".scss", ".less"}
PDF_EXTS = {".pdf"}
WORD_EXTS = {".doc", ".docx", ".rtf", ".odt"}
EXCEL_EXTS = {".xls", ".xlsx", ".csv", ".ods"}
PPT_EXTS = {".ppt", ".pptx", ".odp"}
OFFICE_EXTS = WORD_EXTS | EXCEL_EXTS | PPT_EXTS


def _detect_type(file_path: str) -> str:
    """根据扩展名判断文件类型，返回 'pdf' | 'image' | 'text' | 'office' | 'unknown'。"""
    ext = os.path.splitext(file_path)[1].lower()
    if ext in PDF_EXTS:
        return "pdf"
    if ext in IMAGE_EXTS:
        return "image"
    if ext in TEXT_EXTS:
        return "text"
    if ext in OFFICE_EXTS:
        return "office"
    return "unknown"


class DocumentPreviewDialog(QDialog):
    """文档预览对话框。

    接收文件路径，自动判断类型并选择合适的预览方式。
    提供"在默认应用打开"和"分享（复制到指定路径）"功能。
    """

    def __init__(self, file_path: str, parent=None):
        super().__init__(parent)
        self._original_file_path = os.path.abspath(file_path)
        self.file_path = self._original_file_path
        self._file_type = _detect_type(self.file_path)
        self._temp_pdf_path: Optional[str] = None  # Office 转 PDF 的临时文件

        self.setWindowTitle(f"预览 · {os.path.basename(self.file_path)}")
        self.setWindowIcon(app_icon())
        self.setObjectName("root")
        self.setMinimumSize(720, 560)
        self.resize(820, 640)

        self._build_ui()
        self._center()
        # 延迟加载预览内容，让窗口先显示出来
        QTimer.singleShot(50, self._load_preview)

    # ------------------------------------------------------------------
    # UI 构建
    # ------------------------------------------------------------------
    def _center(self) -> None:
        """窗口居中显示。"""
        screen = QGuiApplication.primaryScreen().availableGeometry()
        self.move(screen.center().x() - self.width() // 2,
                  screen.center().y() - self.height() // 2)

    def _build_ui(self) -> None:
        """构建界面布局。"""
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 顶部：文件信息栏 ----
        info_bar = self._build_info_bar()
        layout.addWidget(info_bar)

        # ---- 中部：预览区域 ----
        self.preview_area = QScrollArea()
        self.preview_area.setWidgetResizable(True)
        self.preview_area.setFrameShape(QFrame.NoFrame)
        self.preview_area.setStyleSheet(f"""
            QScrollArea {{
                background: {CARD};
                border: 1px solid {BORDER};
                border-radius: 12px;
            }}
        """)

        # 占位：加载提示
        self._preview_placeholder = QLabel("正在加载预览…")
        self._preview_placeholder.setAlignment(Qt.AlignCenter)
        self._preview_placeholder.setStyleSheet(f"color: {TEXT_SECONDARY}; padding: 40px;")
        self.preview_area.setWidget(self._preview_placeholder)

        layout.addWidget(self.preview_area, 1)

        # ---- 底部：操作按钮 ----
        button_row = QHBoxLayout()
        button_row.setSpacing(8)

        self.share_btn = QPushButton("分享…")
        self.share_btn.setCursor(Qt.PointingHandCursor)
        self.share_btn.clicked.connect(self._on_share)

        self.open_default_btn = QPushButton("在默认应用打开")
        self.open_default_btn.setCursor(Qt.PointingHandCursor)
        self.open_default_btn.clicked.connect(self._on_open_default)

        self.close_btn = QPushButton("关闭")
        self.close_btn.setObjectName("primary")
        self.close_btn.setCursor(Qt.PointingHandCursor)
        self.close_btn.clicked.connect(self.accept)

        button_row.addWidget(self.share_btn)
        button_row.addStretch()
        button_row.addWidget(self.open_default_btn)
        button_row.addWidget(self.close_btn)

        layout.addLayout(button_row)

    def _build_info_bar(self) -> QFrame:
        """构建顶部文件信息卡片。"""
        card = QFrame()
        card.setObjectName("card")
        card.setMinimumHeight(72)

        card_layout = QHBoxLayout(card)
        card_layout.setContentsMargins(16, 12, 16, 12)
        card_layout.setSpacing(14)

        # 文件名 + 路径
        info_layout = QVBoxLayout()
        info_layout.setSpacing(2)

        name_label = QLabel(os.path.basename(self.file_path))
        name_label.setStyleSheet("font-size: 14px; font-weight: 600;")
        name_label.setWordWrap(True)

        # 文件大小
        try:
            size_str = format_size(os.path.getsize(self.file_path))
        except OSError:
            size_str = "未知大小"

        type_label = QLabel(f"{self._type_label()}  ·  {size_str}  ·  "
                            f"{os.path.dirname(self.file_path)}")
        type_label.setStyleSheet(f"color: {TEXT_SECONDARY}; font-size: 12px;")
        type_label.setWordWrap(True)

        info_layout.addWidget(name_label)
        info_layout.addWidget(type_label)

        card_layout.addLayout(info_layout, 1)

        return card

    def _type_label(self) -> str:
        """返回文件类型的中文描述。"""
        mapping = {
            "pdf": "PDF 文档",
            "image": "图片",
            "text": "文本文件",
            "office": "Office 文档",
            "unknown": "文件",
        }
        return mapping.get(self._file_type, "文件")

    # ------------------------------------------------------------------
    # 预览加载
    # ------------------------------------------------------------------
    def _load_preview(self) -> None:
        """根据文件类型加载对应预览。"""
        if not os.path.exists(self.file_path):
            self._show_error("文件不存在")
            return

        try:
            if self._file_type == "pdf":
                self._load_pdf_preview()
            elif self._file_type == "image":
                self._load_image_preview()
            elif self._file_type == "text":
                self._load_text_preview()
            elif self._file_type == "office":
                self._load_office_preview()
            else:
                self._load_unknown_preview()
        except Exception as exc:  # pylint: disable=broad-except
            self._show_error(f"预览加载失败：{exc}")

    def _set_preview_widget(self, widget: QWidget) -> None:
        """替换预览区的内容控件。"""
        old_widget = self.preview_area.widget()
        self.preview_area.setWidget(widget)
        if old_widget is not None and old_widget is not self._preview_placeholder:
            old_widget.deleteLater()

    # -- PDF 预览 --
    def _load_pdf_preview(self) -> None:
        """加载 PDF 预览（使用 QPdfReader 渲染首页为图片）。"""
        try:
            from PySide6.QtPdf import QPdfDocument
            from PySide6.QtPdfWidgets import QPdfView
            # 优先使用 QPdfView（完整 PDF 查看器）
            pdf_view = QPdfView()
            pdf_doc = QPdfDocument(self)
            pdf_doc.load(self.file_path)
            pdf_view.setDocument(pdf_doc)
            pdf_view.setPageMode(QPdfView.PageMode.MultiPage)
            pdf_view.setZoomMode(QPdfView.FitToWidth)
            self._set_preview_widget(pdf_view)
        except Exception:
            # QtPdf 模块不可用或渲染失败，降级为首页渲染
            self._load_pdf_fallback()

    def _load_pdf_fallback(self) -> None:
        """PDF 预览降级方案：尝试用 QImageReader 或直接显示提示。"""
        try:
            # 尝试通过 PyMuPDF (fitz) 渲染
            import fitz  # type: ignore
            doc = fitz.open(self.file_path)
            if doc.page_count > 0:
                page = doc[0]
                pix = page.get_pixmap(dpi=150)
                img = QPixmap()
                img.loadFromData(pix.tobytes("png"))
                self._show_image_preview(img)
                doc.close()
                return
        except ImportError:
            pass
        except Exception:  # pylint: disable=broad-except
            pass

        # 最终降级：显示提示卡片
        self._show_unsupported_card(
            "PDF 预览组件不可用",
            "当前环境缺少 PDF 渲染组件，您可以使用系统默认应用打开此文件。"
        )

    # -- 图片预览 --
    def _load_image_preview(self) -> None:
        """加载图片预览。"""
        pixmap = QPixmap(self.file_path)
        if pixmap.isNull():
            self._show_error("无法加载图片")
            return
        self._show_image_preview(pixmap)

    def _show_image_preview(self, pixmap: QPixmap) -> None:
        """在预览区显示图片（自适应缩放）。"""
        label = QLabel()
        label.setAlignment(Qt.AlignCenter)
        label.setStyleSheet(f"background: {CARD}; padding: 10px;")

        # 按预览区大小缩放，保留比例
        area_size = self.preview_area.size() - QSize(40, 40)
        scaled = pixmap.scaled(
            area_size,
            Qt.KeepAspectRatio,
            Qt.SmoothTransformation,
        )
        label.setPixmap(scaled)
        self._set_preview_widget(label)

    # -- 文本预览 --
    def _load_text_preview(self) -> None:
        """加载文本/代码预览。"""
        # 限制读取大小，避免大文件卡 UI
        max_bytes = 2 * 1024 * 1024  # 2MB
        try:
            file_size = os.path.getsize(self.file_path)
            truncated = file_size > max_bytes

            with open(self.file_path, "r", encoding="utf-8", errors="replace") as f:
                if truncated:
                    content = f.read(max_bytes)
                    content += f"\n\n… 文件过大，仅预览前 {format_size(max_bytes)} …"
                else:
                    content = f.read()
        except Exception as exc:  # pylint: disable=broad-except
            self._show_error(f"读取文件失败：{exc}")
            return

        text_edit = QPlainTextEdit()
        text_edit.setReadOnly(True)
        text_edit.setPlainText(content)
        text_edit.setStyleSheet(f"""
            QPlainTextEdit {{
                background: {CARD};
                border: none;
                font-family: "Cascadia Mono", "Consolas", "Microsoft YaHei UI", monospace;
                font-size: 12px;
                padding: 12px;
                color: {TEXT};
            }}
        """)
        text_edit.setLineWrapMode(QPlainTextEdit.NoWrap)
        self._set_preview_widget(text_edit)

    # -- Office 文档预览 --
    def _load_office_preview(self) -> None:
        """加载 Office 文档预览：优先 pywin32 转 PDF，否则展示信息卡片。"""
        converted = self._convert_office_to_pdf()
        if converted:
            # 复用 PDF 预览逻辑
            self._file_type = "pdf"
            self._load_pdf_preview()
            return

        # 无法转换，展示信息卡片
        self._show_unsupported_card(
            "Office 文档预览",
            "当前环境未安装 pywin32 或 Office，无法直接预览。\n"
            "您可以点击下方按钮在系统默认应用中打开此文件。"
        )

    def _convert_office_to_pdf(self) -> bool:
        """尝试用 pywin32 将 Office 文档转换为 PDF。

        Returns:
            转换成功返回 True（结果写入 self._temp_pdf_path），失败返回 False。
        """
        ext = os.path.splitext(self.file_path)[1].lower()
        temp_dir = tempfile.gettempdir()
        base_name = os.path.splitext(os.path.basename(self.file_path))[0]
        output_path = os.path.join(temp_dir, f"{base_name}_preview.pdf")

        try:
            import win32com.client  # type: ignore
            import pythoncom  # type: ignore
        except ImportError:
            return False

        try:
            pythoncom.CoInitialize()
            if ext in WORD_EXTS:
                word = win32com.client.DispatchEx("Word.Application")
                word.Visible = False
                doc = word.Documents.Open(self.file_path)
                doc.SaveAs(output_path, FileFormat=17)  # 17 = wdFormatPDF
                doc.Close(False)
                word.Quit()
            elif ext in EXCEL_EXTS:
                excel = win32com.client.DispatchEx("Excel.Application")
                excel.Visible = False
                wb = excel.Workbooks.Open(self.file_path)
                wb.ExportAsFixedFormat(0, output_path)  # 0 = xlTypePDF
                wb.Close(False)
                excel.Quit()
            elif ext in PPT_EXTS:
                ppt = win32com.client.DispatchEx("PowerPoint.Application")
                ppt.Visible = True  # PowerPoint 要求 Visible 才能导出
                prs = ppt.Presentations.Open(self.file_path, WithWindow=False)
                prs.SaveAs(output_path, 32)  # 32 = ppSaveAsPDF
                prs.Close()
                ppt.Quit()
            else:
                return False

            if os.path.exists(output_path) and os.path.getsize(output_path) > 0:
                self._temp_pdf_path = output_path
                # 把 file_path 替换成转换后的 PDF，走 PDF 预览逻辑
                self.file_path = output_path
                return True
            return False
        except Exception:  # pylint: disable=broad-except
            return False
        finally:
            try:
                pythoncom.CoUninitialize()
            except Exception:  # pylint: disable=broad-except
                pass

    # -- 未知类型 --
    def _load_unknown_preview(self) -> None:
        """未知文件类型：展示信息卡片。"""
        self._show_unsupported_card(
            "暂不支持预览此文件类型",
            "您可以点击下方按钮在系统默认应用中打开此文件。"
        )

    # -- 通用：错误 / 不支持卡片 --
    def _show_error(self, message: str) -> None:
        """显示错误信息。"""
        self._show_info_card("预览失败", message, is_error=True)

    def _show_unsupported_card(self, title: str, description: str) -> None:
        """显示不支持预览的信息卡片。"""
        self._show_info_card(title, description, is_error=False)

    def _show_info_card(self, title: str, description: str, is_error: bool = False) -> None:
        """展示居中的信息卡片（用于错误提示 / 不支持的类型）。"""
        container = QWidget()
        container.setStyleSheet(f"background: {CARD};")

        outer = QVBoxLayout(container)
        outer.setAlignment(Qt.AlignCenter)
        outer.setContentsMargins(40, 60, 40, 60)

        card = QFrame()
        card.setObjectName("card")
        card.setMaximumWidth(420)
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(24, 20, 24, 20)
        card_layout.setSpacing(10)

        title_label = QLabel(title)
        title_label.setAlignment(Qt.AlignCenter)
        title_label.setStyleSheet(
            f"font-size: 15px; font-weight: 600; "
            f"color: {'#D64545' if is_error else TEXT};"
        )

        desc_label = QLabel(description)
        desc_label.setAlignment(Qt.AlignCenter)
        desc_label.setWordWrap(True)
        desc_label.setStyleSheet(f"color: {TEXT_SECONDARY}; font-size: 13px;")

        open_btn = QPushButton("在默认应用打开")
        open_btn.setObjectName("primary")
        open_btn.setCursor(Qt.PointingHandCursor)
        open_btn.clicked.connect(self._on_open_default)

        card_layout.addWidget(title_label)
        card_layout.addWidget(desc_label)
        card_layout.addSpacing(6)
        card_layout.addWidget(open_btn)

        outer.addWidget(card)

        self._set_preview_widget(container)

    # ------------------------------------------------------------------
    # 按钮事件
    # ------------------------------------------------------------------
    def _on_open_default(self) -> None:
        """用系统默认应用打开文件。"""
        try:
            if os.name == "nt":
                os.startfile(self._original_path())  # type: ignore[attr-defined]
            else:
                import subprocess
                subprocess.Popen(["xdg-open", self._original_path()])
        except Exception as exc:  # pylint: disable=broad-except
            QMessageBox.warning(self, "打开失败", f"无法打开文件：{exc}")

    def _original_path(self) -> str:
        """获取原始文件路径（Office 转 PDF 后，返回原 Office 文件路径）。"""
        return self._original_file_path

    def _on_share(self) -> None:
        """分享：将文件复制到用户选择的目录。"""
        target_dir = QFileDialog.getExistingDirectory(
            self, "选择保存位置（将复制文件到此目录）",
            os.path.expanduser("~"))
        if not target_dir:
            return

        src = self._original_path()
        dst = os.path.join(target_dir, os.path.basename(src))

        # 目标已存在则自动重命名
        if os.path.exists(dst):
            base, ext = os.path.splitext(dst)
            idx = 1
            while os.path.exists(f"{base} ({idx}){ext}"):
                idx += 1
            dst = f"{base} ({idx}){ext}"

        try:
            shutil.copy2(src, dst)
            QMessageBox.information(self, "分享完成",
                                    f"文件已复制到：\n{dst}")
        except Exception as exc:  # pylint: disable=broad-except
            QMessageBox.warning(self, "复制失败", f"无法复制文件：{exc}")

    # ------------------------------------------------------------------
    def closeEvent(self, event) -> None:  # noqa: N802 (Qt 命名约定)
        """关闭时清理临时文件。"""
        if self._temp_pdf_path and os.path.exists(self._temp_pdf_path):
            try:
                os.remove(self._temp_pdf_path)
            except OSError:
                pass
        super().closeEvent(event)
