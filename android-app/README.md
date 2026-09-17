# 复小学 · Android 版

「复小学」复旦大学 eLearning (Canvas LMS) 课程文件同步工具的 Android 客户端，
与 [桌面端](../elearning-sync/) 共用同一套 eLearning 认证与同步协议，
UI 风格、配色与应用图标保持一致。

## 功能特性

- **统一身份认证登录**：UIS 账号密码（RSA 加密），会话持久化，打开即同步
- **课程列表**：学期、文件数、已下载大小一目了然
- **文件列表**：按课程查看全部文件与下载状态
- **文件预览 / 分享**：系统 Intent 打开（PDF / Word / Excel / PPT），ShareSheet 分享
- **后台同步**：WorkManager 定时增量同步（默认 15 分钟）
- **下载通知**：新文件下载完成时发送系统通知
- **存储管理**：按学期 / 课程筛选删除文件，释放空间

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- MVVM：`AppViewModel` + `StateFlow`
- OkHttp + 自实现 Canvas API（与桌面端 `fudan_sync` 协议同源）
- Room / SQLite 状态库（与桌面端 `sync_state.db` 结构一致）
- WorkManager 后台同步 + 前台服务

## 项目结构

```text
android-app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/edu/fudan/elearning/sync/
│       │   ├── App.kt                    # Application 入口
│       │   ├── MainActivity.kt           # Compose 宿主
│       │   ├── auth/UisAuthenticator.kt  # UIS 登录（RSA + id.fudan.edu.cn）
│       │   ├── data/                     # Room 数据库、DAO、Repo
│       │   ├── network/                  # OkHttp、CanvasApi、CookieJar
│       │   ├── sync/                     # 同步引擎、下载管理
│       │   ├── ui/                       # LoginScreen / HomeScreen / Theme
│       │   ├── util/                     # Prefs、SecurePrefs、FileUtils
│       │   └── worker/                   # SyncWorker、通知
│       └── res/
│           ├── drawable/ic_launcher.xml         # 学士帽图标矢量
│           ├── drawable-anydpi-v26/ic_launcher.xml  # 自适应图标
│           ├── values/strings.xml               # app_name = 复小学
│           └── values/themes.xml                # Theme.FuXiaoXue
└── release.keystore                     # 签名（密码在 local.properties）
```

## 应用信息

- **应用名称**：复小学
- **包名**：`edu.fudan.elearning.sync`
- **版本**：1.0.3（versionCode 4）
- **最低 Android 版本**：8.0（API 26）
- **目标 Android 版本**：14（API 34）

## 开发环境搭建

### 前置要求

- Android Studio（Ladytail 或更新）
- Android SDK（compileSdk 36）
- JDK 17+

### 构建运行

```bash
cd android-app
./gradlew assembleRelease     # 或在 Android Studio 中直接 Run
./gradlew installRelease      # 安装到已连接设备
```

> 签名密钥 `release.keystore` 在仓库内，密码通过 `local.properties` 的
> `fudanSign.*` 配置；克隆后若未配置会自动回退 debug 签名，保证可独立构建。

## 与桌面端的统一

| 项目 | 桌面端（PySide6） | Android 端（Compose） |
|------|-------------------|----------------------|
| 主色 | `#4F46E5` Indigo 600 | `0xFF4F46E5` |
| 悬停 / 深色 | `#4338CA` / `#312E81` | `0xFF4338CA` / `0xFF312E81` |
| 背景 | `#F6F7FC` | `0xFFF6F7FC` |
| 图标 | 圆角靛蓝方形 + 白色学士帽 | 同一矢量几何 |
| 状态栏 | — | `#4338CA` |

图标为同一学士帽几何（帽板 / 帽箍 / 帽穗），桌面端 `build_assets/app.ico`
与 Android `ic_launcher.xml` 共用设计。

## Android 权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | 访问 eLearning 平台 |
| `ACCESS_NETWORK_STATE` | 检查网络状态 |
| `POST_NOTIFICATIONS` | 下载 / 同步通知（Android 13+） |
| `FOREGROUND_SERVICE` | 前台同步服务 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 数据同步前台服务类型 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启后台同步 |
| `WAKE_LOCK` | 同步过程中设备不休眠 |
| `READ_EXTERNAL_STORAGE` | 读取外部存储（兼容旧版） |
| `WRITE_EXTERNAL_STORAGE` | 写入外部存储（兼容旧版） |

## License

MIT
