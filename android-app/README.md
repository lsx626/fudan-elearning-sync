# 复旦 eLearning 同步 - Android 版

基于 [BeeWare (Toga)](https://beeware.org/) 框架构建的复旦大学 eLearning (Canvas LMS) 课程文件自动同步工具 Android 客户端。

## 功能特性

- **统一身份认证登录**：复用 fudan_sync 新版 UIS 登录（RSA 加密 + id.fudan.edu.cn）
- **课程列表**：Toga Table 展示课程名、学期、文件数、已下载大小
- **文件列表**：点击课程展开查看所有文件及下载状态
- **文件预览**：调用 Android 系统 Intent 打开文件（PDF/Word/Excel/PPT 用系统默认应用）
- **文件分享**：Android ShareSheet 分享文件给其他应用
- **后台同步**：使用 WorkManager / AlarmManager 定时同步
- **下载通知**：新文件下载完成时发送 Android 通知
- **存储管理**：按学期/课程筛选删除文件，释放存储空间
- **同步频率设置**：用户可配置（默认 15 分钟）

## 项目结构

```
android-app/
├── pyproject.toml              # BeeWare 项目配置
├── src/
│   └── fudan_elearning_sync/
│       ├── __init__.py         # 包初始化
│       ├── __main__.py         # 应用入口
│       ├── app.py              # Toga App 主类与 UI 页面
│       ├── sync_core.py        # 同步核心（封装 fudan_sync）
│       └── android_utils.py    # Android 专属功能
├── resources/
│   ├── icon.png                # 应用图标（占位图）
│   ├── splash.png              # 启动页（占位图）
│   └── _generate_placeholders.py  # 占位图生成脚本
└── README.md                   # 本文件
```

## 技术架构

### 核心模块

| 模块 | 职责 |
|------|------|
| `app.py` | Toga UI 主类，包含登录页、课程列表、文件列表、设置页、存储管理页 |
| `sync_core.py` | 封装 fudan_sync 核心逻辑，提供线程安全的异步接口 |
| `android_utils.py` | Android 平台专属功能：通知、Intent、分享、后台同步等 |

### fudan_sync 依赖

本项目复用上级目录 `../elearning-sync/fudan_sync/` 中的核心逻辑：

- `password_login` — UIS 账号密码登录（RSA 加密）
- `auth` — Cookie 认证管理
- `canvas_api` — Canvas LMS REST API 客户端
- `sync_engine` — 同步引擎（课程发现、增量比对、下载）
- `state` — SQLite 状态存储
- `config` — 配置管理
- `utils` — 通用工具函数

导入路径在 `sync_core.py` 中自动设置，将 `elearning-sync/` 加入 `sys.path`。

## 应用信息

- **应用名称**：复旦 eLearning 同步
- **包名**：`edu.fudan.elearning.sync`
- **版本号**：1.0.0
- **最低 Android 版本**：8.0 (API 26)
- **目标 Android 版本**：14.0 (API 34)
- **UI 框架**：Toga (BeeWare)
- **语言**：Python 3.10+

## 开发环境搭建

### 前置要求

- Python 3.10+
- BeeWare Briefcase
- Android SDK（用于构建 APK）
- Java 11+

### 安装依赖

```bash
# 安装 BeeWare 工具链
pip install briefcase toga

# 进入项目目录
cd android-app
```

### 桌面端调试

```bash
# 直接运行（使用系统默认 Toga 后端，如 Windows/macOS/Linux）
cd android-app
python -m fudan_elearning_sync
```

> 注意：桌面端调试时，`android_utils.py` 会使用模拟实现，不会调用真正的 Android API。

### 构建 Android 应用

```bash
# 初始化 Android 项目（首次）
briefcase create android

# 构建 APK
briefcase build android

# 运行到连接的设备或模拟器
briefcase run android

# 打包发布版
briefcase package android
```

## UI 页面

### 1. 登录页
- 学号/工号 + 密码输入
- RSA 加密传输密码
- 自动填充已保存的账号

### 2. 课程列表页
- Table 展示：课程名称、学期、文件数、已下载大小
- 顶部同步状态显示（进度条 + 文字）
- 底部"立即同步"按钮
- 右上角设置入口

### 3. 文件列表页
- 显示指定课程的所有文件
- 文件状态：已下载 / 待下载 / 下载失败 / 已删除
- 点击已下载文件调用系统应用打开
- 支持同步单个课程

### 4. 设置页
- 同步频率设置（分钟）
- 仅 WiFi 同步开关
- 存储管理入口
- 账号信息与退出登录

### 5. 存储管理页
- 总存储使用量展示
- 按大小排序的课程列表
- 点击课程删除本地文件

## Android 权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | 访问 eLearning 平台 |
| `ACCESS_NETWORK_STATE` | 检查网络状态 |
| `POST_NOTIFICATIONS` | 发送下载/同步通知（Android 13+） |
| `FOREGROUND_SERVICE` | 前台同步服务 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 数据同步前台服务类型 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启动后台同步 |
| `WAKE_LOCK` | 保证同步过程中设备不休眠 |
| `READ_EXTERNAL_STORAGE` | 读取外部存储（兼容旧版） |
| `WRITE_EXTERNAL_STORAGE` | 写入外部存储（兼容旧版） |

## 注意事项

1. **fudan_sync 路径**：`sync_core.py` 假设 fudan_sync 位于
   `../../elearning-sync/fudan_sync/`（相对于 android-app 目录）。
   如目录结构不同，请修改 `_FUDAN_SYNC_PARENT` 变量。

2. **资源文件**：`resources/icon.png` 和 `resources/splash.png` 是占位图，
   正式发布前请替换为专业设计的图标（512x512 和 1080x1920）。

3. **FileProvider**：文件预览和分享功能需要配置 Android FileProvider，
   在 `AndroidManifest.xml` 中添加相应的 provider 声明。
   BeeWare Briefcase 可能需要通过自定义 Android 模板来配置。

4. **WorkManager**：后台同步使用 AndroidX WorkManager，
   需要添加相应的依赖和 Worker 类（Java/Kotlin 实现）。

5. **Material Design**：Toga 原生组件有限，部分 Material Design 效果
   通过样式模拟，实际运行效果可能因平台而异。

## License

MIT
