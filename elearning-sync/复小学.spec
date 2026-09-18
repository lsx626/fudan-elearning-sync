# -*- mode: python ; coding: utf-8 -*-

from PyInstaller.utils.hooks import collect_submodules

block_cipher = None


try:
    from PySide6.QtMultimedia import QMediaPlayer
    from PySide6.QtMultimediaWidgets import QVideoWidget
except (ImportError, OSError) as exc:
    raise RuntimeError(
        "Windows release builds require a complete matching PySide6-Addons "
        "installation. Reinstall requirements.txt before packaging."
    ) from exc

hiddenimports = [
    "PySide6.QtMultimedia",
    "PySide6.QtMultimediaWidgets",
    "PySide6.QtPdf",
    "PySide6.QtPdfWidgets",
    # The previewer loads these readers only when the matching file type is
    # opened.  Keep them explicit so release builds do not depend on
    # PyInstaller recognizing imports inside optional fallback branches.
    "docx",
    "openpyxl",
    "pptx",
    "odf",
    "odf.draw",
    "odf.opendocument",
    "odf.table",
    "odf.teletype",
    "odf.text",
    "PIL.Image",
    "pillow_heif",
    "fitz",
    "pymupdf",
]
# Keep any transitive multimedia imports that PyInstaller cannot see through
# the previewer's lazy imports.
try:
    hiddenimports += collect_submodules("PySide6.QtMultimedia")
except Exception:
    pass

a = Analysis(
    ['gui.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='复小学',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=['build_assets/app.ico'],
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='复小学',
)
