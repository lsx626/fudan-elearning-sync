# 复旦大学 eLearning 课程文件同步工具

把 **elearning.fudan.edu.cn**（基于 Canvas LMS）上每门课程的全部文件自动下载到本地，
并通过守护进程定时增量比对，保持网站与本地的同步。

## 功能特性

- **全覆盖采集**：课程"文件"工具的全部目录、模块(Module)附件、页面(Page)、作业(Assignment)、
  公告(Announcement)、大纲(Syllabus) 中引用的一切文件
- **目录结构还原**：按 Canvas 目录树在本地重建 `<课程名> [<课程代码>]/<子目录>/<文件名>`
- **增量同步**：SQLite 状态库记录每个文件的 size / updated_at / modified_at，仅下载新增与变更
- **实时更新**：`daemon` 守护进程按间隔轮询（Canvas 不向学生推送变更，轮询是唯一可行方案）
- **页面归档**：页面/作业/公告正文导出为 HTML 存到 `<课程>/_pages/`，保留非文件类内容
- **稳健下载**：断点续传（HTTP Range）、并发下载、大小校验、自动重试、429 限流退避
- **三种登录方式**：API Token / 浏览器交互登录（UIS）/ 手动导入 Cookie

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

> 需要 Python 3.10+。浏览器登录方式还需额外执行 `playwright install chromium`。

### 2. 登录

首次运行任意命令时会自动从 `config.example.yaml` 生成 `config.yaml`。

**方式一：API Token（推荐）**

登录 eLearning → 右上角头像 → 设置 → 批准的集成(Approved Integrations)
→ 新建访问令牌(New Access Token)，复制后：

```bash
python sync.py login --method token --token <你的Token>
# 或设置环境变量后直接登录
set FUDAN_ELEARNING_TOKEN=<你的Token>
python sync.py login --method token
```

**方式二：浏览器交互登录（UIS 统一身份认证）**

```bash
python sync.py login --method browser
```

会打开浏览器，完成 UIS 登录与可能的验证码后自动保存 Cookie，后续无需重复登录。

**方式三：手动导入 Cookie**

把浏览器中的 Cookie 导出为 JSON（含 `_canvas_session` 等），保存为 `cookies.json`：

```bash
python sync.py login --method cookie
```

三种方式都会调用 `/api/v1/users/self` 验证凭据是否有效。

### 3. 列出课程

```bash
python sync.py courses
```

### 4. 执行一次同步

```bash
python sync.py sync               # 增量同步（默认）
python sync.py sync --full        # 全量同步，忽略本地状态重新下载
python sync.py sync --courses 12345 67890   # 仅同步指定课程 ID
```

### 5. 保持实时更新（守护进程）

```bash
python sync.py daemon             # 按配置的间隔持续轮询同步
python sync.py daemon --interval 15   # 每 15 分钟一轮
python sync.py daemon --single    # 只跑一轮后退出（调试用）
```

守护进程首轮全量同步，之后每轮增量比对。按 `Ctrl+C` 可安全中断（会完成当前文件后退出）。

### 6. 查看状态

```bash
python sync.py status             # 汇总：课程数、文件数、已下载总量、最近同步
python sync.py status --courses   # 每门课程的下载明细
python sync.py files              # 列出本地已记录的全部文件
python sync.py files --course 12345
```

## 配置说明

编辑 `config.yaml`（首次运行自动生成），完整示例见 `config.example.yaml`：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `base_url` | 平台地址 | `https://elearning.fudan.edu.cn` |
| `auth.method` | 认证方式：`token` / `cookie` / `browser` | `token` |
| `auth.token` | API Token（也可用环境变量 `FUDAN_ELEARNING_TOKEN`） | 空 |
| `auth.method_cookie_file` | Cookie 文件路径 | `cookies.json` |
| `root_dir` | 本地下载根目录 | `./elearning_files` |
| `state_db` | 状态数据库路径 | `./sync_state.db` |
| `log_file` | 日志文件（空 = 仅控制台） | `./sync.log` |
| `sync.interval_minutes` | 守护进程轮询间隔（分钟） | `30` |
| `sync.only_favorites` | 仅同步星标收藏课程 | `false` |
| `sync.enrollment_type` | 角色过滤：student/teacher/ta/observer/designer | `student` |
| `sync.include_courses` / `exclude_courses` | 课程 ID 白/黑名单 | `[]` |
| `sync.include_terms` | 学期过滤（模糊匹配，如 `["2025春季"]`） | `[]` |
| `sync.download.concurrency` | 文件下载并发数（API 调用始终串行以规避限流） | `4` |
| `sync.download.max_retries` | 单文件重试次数 | `5` |
| `sync.download.max_file_size_mb` | 单文件大小上限，`0` = 不限制 | `0` |
| `sync.download.min_free_space_gb` | 磁盘剩余空间低于此值时停止 | `1` |
| `sync.download.exclude_extensions` | 跳过的扩展名，如 `[".exe", ".iso"]` | `[]` |
| `sync.prune` | 远端删除时是否同步删除本地文件 | `false` |
| `sync.archive_pages` | 页面/作业/公告正文归档为 HTML | `true` |

## 本地目录结构

同步完成后，`root_dir` 下形如：

```
elearning_files/
└── 高等数学（A） [MATH101]/
    ├── 课件/
    │   ├── 第1周讲义.pdf
    │   └── 第2周讲义.pdf
    ├── 作业/
    │   └── 作业参考答案.docx
    └── _pages/
        ├── 页面 - 课程介绍.html
        ├── 作业 - 作业1.html
        └── 公告 - 开课通知.html
```

- 课程目录名格式为 `<课程名> [<课程代码>]`，已做 Windows 非法字符清洗
- `_pages/` 存放正文归档，链接已改写为绝对路径，可离线浏览
- 被教师锁定（`locked_for_user`）的文件不会下载
- 远端删除的文件默认保留本地副本（状态库标记为 missing）；开启 `prune` 后才会删除

## 工作原理

1. **课程发现**：`GET /api/v1/courses`，按学期/白黑名单/收藏过滤
2. **内容爬取**：目录树(`folders`) → 文件列表(`files`) → 模块(`modules`)
   → 页面/作业/公告/大纲正文中的文件链接，全部汇总去重
3. **增量判定**：与 SQLite 状态库比对 size / updated_at / modified_at / 本地文件存在性
4. **下载**：先调 `/courses/:id/files/:id` 换取签名下载 URL，流式下载到 `.part`
   临时文件，大小校验通过后原子改名
5. **轮询同步**：守护进程重复 1-4，完成"实时更新"效果

## 注意事项

- **Token 权限**：访问令牌等同于账号权限，请勿泄露；`config.yaml` 与 `cookies.json`
  建议加入 `.gitignore`
- **平台限制**：Canvas 不向学生提供文件变更推送，"实时"是分钟级轮询，非秒级
- **限流**：API 请求串行执行并遵守 `X-Rate-Limit-Remaining` / 429 退避，正常使用不会触发
- **锁定的文件**：教师设置了访问限制的文件无法下载，属正常现象
- **隐私合规**：仅同步你有权访问的课程内容，请遵守学校相关规定

## 项目结构

```
elearning-sync/
├── sync.py                  # CLI 入口（login/courses/sync/daemon/status/files）
├── config.example.yaml      # 配置示例
├── requirements.txt
└── fudan_sync/
    ├── auth.py              # 认证：Token / Cookie / 浏览器 UIS 登录
    ├── canvas_api.py        # Canvas API 客户端：分页、限流退避、重试
    ├── config.py            # 配置加载与校验
    ├── crawler.py           # 内容爬虫：文件/模块/页面/作业/公告/大纲
    ├── downloader.py        # 下载器：断点续传、并发、校验
    ├── state.py             # SQLite 状态库（WAL）
    ├── sync_engine.py       # 同步引擎：编排爬取→比对→下载→归档
    ├── daemon.py            # 守护进程
    └── utils.py             # 工具函数
```
