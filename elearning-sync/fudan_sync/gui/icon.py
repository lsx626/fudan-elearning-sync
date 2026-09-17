"""用 QPainter 现场绘制应用图标（蓝色圆角方块 + 白色“同步”环），不依赖外部资源。"""
from __future__ import annotations

import math

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import (QBrush, QColor, QIcon, QImage, QLinearGradient,
                           QPainter, QPen, QPixmap, QRadialGradient)
from PySide6.QtWidgets import QApplication


def _paint_mark(painter: QPainter, size: float) -> None:
    """在已铺好底色的画布中央绘制白色同步环。"""
    center = QPointF(size / 2, size / 2)
    radius = size * 0.30
    pen_width = max(3.0, size * 0.085)

    # 两段弧 + 两处缺口，构成“循环”视觉
    gap = 26.0  # 缺口角度（度）
    arc_span = 180.0 - gap  # 每段弧的跨度

    rect = QRectF(center.x() - radius, center.y() - radius,
                  radius * 2, radius * 2)

    painter.save()
    painter.setRenderHint(QPainter.Antialiasing, True)
    pen = QPen(QColor("#FFFFFF"), pen_width)
    pen.setCapStyle(Qt.RoundCap)
    painter.setPen(pen)
    painter.setBrush(Qt.NoBrush)

    for offset in (0.0, 180.0):
        start = offset + gap / 2  # Qt 角度：从 3 点钟方向开始，正值逆时针
        painter.drawArc(rect, int(start * 16), int(arc_span * 16))
    painter.restore()

    # 箭头：在每段弧的顺时针末端补一个三角形
    painter.save()
    painter.setRenderHint(QPainter.Antialiasing, True)
    painter.setPen(Qt.NoPen)
    painter.setBrush(QBrush(QColor("#FFFFFF")))
    arrow_len = pen_width * 1.5
    for offset in (0.0, 180.0):
        # 弧顺时针末端角度（屏幕坐标系，从 +x 轴起顺时针为正）
        screen_angle = math.radians(-(offset + gap / 2))
        tip = QPointF(center.x() + radius * math.cos(screen_angle),
                      center.y() + radius * math.sin(screen_angle))
        # 切线方向（顺时针）
        tangent_angle = screen_angle + math.pi / 2
        tx = math.cos(tangent_angle)
        ty = math.sin(tangent_angle)
        tip = QPointF(tip.x() + tx * arrow_len * 0.45,
                      tip.y() + ty * arrow_len * 0.45)
        nx, ny = -ty, tx  # 法线
        base = QPointF(tip.x() - tx * arrow_len, tip.y() - ty * arrow_len)
        left = QPointF(base.x() + nx * arrow_len * 0.55,
                       base.y() + ny * arrow_len * 0.55)
        right = QPointF(base.x() - nx * arrow_len * 0.55,
                        base.y() - ny * arrow_len * 0.55)
        painter.drawPolygon([tip, left, right])
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
    radius = size * 0.22
    gradient = QRadialGradient(size * 0.32, size * 0.28, size * 1.1)
    gradient.setColorAt(0.0, QColor("#2E63C9"))
    gradient.setColorAt(0.65, QColor("#1B45A6"))
    gradient.setColorAt(1.0, QColor("#0E2A6B"))
    painter.setBrush(QBrush(gradient))
    painter.setPen(Qt.NoPen)
    painter.drawRoundedRect(rect, radius, radius)

    _paint_mark(painter, size)

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
