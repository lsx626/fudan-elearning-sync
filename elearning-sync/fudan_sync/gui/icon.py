"""用 QPainter 现场绘制应用图标（靛蓝圆角方块 + 白色学士帽），不依赖外部资源。

学士帽是"学习/课堂"的通用符号，契合"复小学"的定位；
配色与 styles.py 及 Android 端 Theme.kt 保持一致。
"""
from __future__ import annotations

import math

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import (QBrush, QColor, QIcon, QImage, QLinearGradient,
                           QPainter, QPen, QPixmap, QPolygonF, QRadialGradient)
from PySide6.QtWidgets import QApplication

# 与 styles.py / Android Theme.kt 共用的品牌色
ICON_COLOR_TOP = "#6366F1"
ICON_COLOR_MID = "#4338CA"
ICON_COLOR_BOTTOM = "#312E81"


def _paint_cap(painter: QPainter, size: float) -> None:
    """在已铺好底色的画布中央绘制白色学士帽。"""
    cx = size / 2
    cy = size * 0.555  # 帽子整体略偏下，视觉上更居中
    board_y = cy - size * 0.085
    bw = size * 0.30   # 帽顶板的半宽
    bh = size * 0.125  # 帽顶板的半高（透视压扁）

    painter.save()
    painter.setRenderHint(QPainter.Antialiasing, True)
    painter.setPen(Qt.NoPen)

    # 帽顶板（菱形）
    board = QPolygonF([
        QPointF(cx, board_y - bh),
        QPointF(cx + bw, board_y),
        QPointF(cx, board_y + bh),
        QPointF(cx - bw, board_y),
    ])
    painter.setBrush(QBrush(QColor("#FFFFFF")))
    painter.drawPolygon(board)

    # 帽身（梯形）：用淡靛蓝与白色形成层次
    band_top = board_y + bh * 0.88
    band_bottom = band_top + size * 0.135
    half_top = size * 0.165
    half_bottom = size * 0.125
    band = QPolygonF([
        QPointF(cx - half_top, band_top),
        QPointF(cx + half_top, band_top),
        QPointF(cx + half_bottom, band_bottom),
        QPointF(cx - half_bottom, band_bottom),
    ])
    painter.setBrush(QBrush(QColor("#DDE2F9")))
    painter.drawPolygon(band)

    # 帽顶板与帽身之间加一道投影缝，增强立体感
    painter.setPen(QPen(QColor("#C7CDF3"), max(1.5, size * 0.012)))
    painter.drawLine(QPointF(cx - half_top * 0.98, band_top),
                     QPointF(cx + half_top * 0.98, band_top))

    # 流苏：从帽右侧垂下，末端一个绒球
    cord_x = cx + bw
    cord_w = max(2.0, size * 0.045)
    cord_pen = QPen(QColor("#FFFFFF"), cord_w)
    cord_pen.setCapStyle(Qt.RoundCap)
    painter.setPen(cord_pen)
    painter.drawLine(QPointF(cord_x, board_y + size * 0.005),
                     QPointF(cord_x, band_bottom + size * 0.015))
    knot_y = band_bottom + size * 0.045
    painter.setBrush(QBrush(QColor("#FFFFFF")))
    painter.setPen(Qt.NoPen)
    painter.drawEllipse(QPointF(cord_x, knot_y), size * 0.03, size * 0.03)
    painter.restore()


def render_pixmap(size: int, device_pixel_ratio: float = 1.0) -> QPixmap:
    """绘制指定尺寸的应用图标位图。"""
    image = QImage(int(size * device_pixel_ratio), int(size * device_pixel_ratio),
                   QImage.Format_ARGB32_Premultiplied)
    image.setDevicePixelRatio(device_pixel_ratio)
    image.fill(Qt.transparent)

    painter = QPainter(image)
    painter.setRenderHint(QPainter.Antialiasing, True)

    # 底：圆角方块 + 径向渐变（左上偏亮）
    rect = QRectF(0, 0, size, size)
    radius = size * 0.24
    gradient = QRadialGradient(size * 0.32, size * 0.26, size * 1.15)
    gradient.setColorAt(0.0, QColor(ICON_COLOR_TOP))
    gradient.setColorAt(0.65, QColor(ICON_COLOR_MID))
    gradient.setColorAt(1.0, QColor(ICON_COLOR_BOTTOM))
    painter.setBrush(QBrush(gradient))
    painter.setPen(Qt.NoPen)
    painter.drawRoundedRect(rect, radius, radius)

    _paint_cap(painter, size)

    # 顶部高光，增加质感
    highlight = QLinearGradient(0, 0, 0, size * 0.5)
    highlight.setColorAt(0.0, QColor(255, 255, 255, 34))
    highlight.setColorAt(1.0, QColor(255, 255, 255, 0))
    painter.setBrush(QBrush(highlight))
    painter.drawRoundedRect(rect, radius, radius)

    painter.end()
    return QPixmap.fromImage(image)


def app_icon() -> QIcon:
    """返回应用图标（自适应高分屏）。"""
    app = QApplication.instance()
    dpr = app.devicePixelRatio() if app is not None else 1.0
    icon = QIcon()
    for size in (16, 24, 32, 48, 64, 128, 256):
        icon.addPixmap(render_pixmap(size, dpr))
    return icon
