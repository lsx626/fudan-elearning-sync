package edu.fudan.elearning.sync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.fudan.elearning.sync.auth.LoginResult
import edu.fudan.elearning.sync.auth.UisAuthenticator
import edu.fudan.elearning.sync.data.CourseStats
import edu.fudan.elearning.sync.data.FileItem
import edu.fudan.elearning.sync.data.Repo
import edu.fudan.elearning.sync.network.ApiClient
import edu.fudan.elearning.sync.network.CanvasApi
import edu.fudan.elearning.sync.sync.DownloadManager
import edu.fudan.elearning.sync.sync.SyncEngine
import edu.fudan.elearning.sync.util.FileUtils
import edu.fudan.elearning.sync.util.Prefs
import edu.fudan.elearning.sync.util.SecurePrefs
import edu.fudan.elearning.sync.worker.Notifier
import edu.fudan.elearning.sync.worker.SyncWorker
import edu.fudan.elearning.sync.sync.SyncGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 登录状态。 */
sealed class LoginState {
    object Loading : LoginState()
    data class LoggedIn(val username: String) : LoginState()
    object LoggedOut : LoginState()
    data class Error(val message: String) : LoginState()
}

/**
 * 预览目标：本地文件 + 在列表中显示的名字。
 *
 * 顶栏标题必须与文件列表一致（Canvas 的 display_name），而不是磁盘文件名
 * （Canvas 的 filename 字段可能是百分号编码的乱码）。
 */
data class PreviewTarget(val file: java.io.File, val title: String)

/** 主界面 ViewModel：登录、同步、课程/文件数据、应用内预览路由。 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = Repo(app)
    private val api = CanvasApi()
    private val downloader = DownloadManager(app)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Loading)
    val loginState: StateFlow<LoginState> = _loginState

    private val _courses = MutableStateFlow<List<CourseStats>>(emptyList())
    val courses: StateFlow<List<CourseStats>> = _courses

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _syncProgress = MutableStateFlow("")
    val syncProgress: StateFlow<String> = _syncProgress

    /** 最近一次同步失败的原因（null 表示没有未处理的错误）。 */
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError

    /** 会话是否已失效：界面据此提示重新登录。 */
    private val _needsReauth = MutableStateFlow(false)
    val needsReauth: StateFlow<Boolean> = _needsReauth

    /**
     * 数据版本号：数据库内容变化时自增。
     *
     * 列表用 `remember(key)` 缓存查询结果时必须带上它，否则删除/同步后界面
     * 会继续显示旧数据（历史缺陷）。
     */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion

    private fun bumpDataVersion() {
        _dataVersion.value = _dataVersion.value + 1
    }

    /** 当前选中的课程（文件列表层）。状态放在 ViewModel 而不是 Composable 里，
     *  保证打开预览离开组合后，返回时仍在原来的文件列表。 */
    private val _selectedCourseId = MutableStateFlow<Long?>(null)
    val selectedCourseId: StateFlow<Long?> = _selectedCourseId

    /** 选中课程的统计信息；课程数据变化时自动更新。 */
    val selectedCourse: StateFlow<CourseStats?> =
        combine(_courses, _selectedCourseId) { list, id ->
            id?.let { targetId -> list.firstOrNull { it.course.id == targetId } }
        }.let { flow ->
            val state = MutableStateFlow<CourseStats?>(null)
            viewModelScope.launch { flow.collect { state.value = it } }
            state
        }

    /** 应用内预览目标（null 表示不在预览态）。 */
    private val _previewTarget = MutableStateFlow<PreviewTarget?>(null)
    val previewTarget: StateFlow<PreviewTarget?> = _previewTarget

    val downloadRoot: String get() = downloader.rootPath()

    init {
        autoLogin()
    }

    /** 启动时自动登录。 */
    private fun autoLogin() {
        val username = prefs.username
        val password = SecurePrefs.loadPassword(getApplication())
        if (username.isEmpty() || password.isNullOrEmpty()) {
            _loginState.value = LoginState.LoggedOut
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            when (val result = UisAuthenticator().login(username, password)) {
                is LoginResult.Success -> {
                    ApiClient.setSession(result.session.canvasSessionCookie, result.session.csrfToken)
                    prefs.loggedIn = true
                    _loginState.value = LoginState.LoggedIn(username)
                    refreshCourses()
                }
                is LoginResult.Failure -> {
                    _loginState.value = LoginState.LoggedOut
                }
            }
        }
    }

    /** 账号密码登录。 */
    fun login(username: String, password: String, remember: Boolean) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            when (val result = UisAuthenticator().login(username, password)) {
                is LoginResult.Success -> {
                    ApiClient.setSession(
                        result.session.canvasSessionCookie,
                        result.session.csrfToken,
                        result.session.cookieName
                    )
                    prefs.username = username
                    prefs.loggedIn = true
                    if (remember) {
                        SecurePrefs.savePassword(getApplication(), password)
                    } else {
                        // 用户选择不记住密码时必须清掉旧密文，否则下次仍会静默登录
                        SecurePrefs.clear(getApplication())
                    }
                    _loginState.value = LoginState.LoggedIn(username)
                    // 安排后台定期同步
                    SyncWorker.schedule(getApplication(), prefs.syncIntervalMinutes)
                    _syncError.value = null
                    _needsReauth.value = false
                    refreshCourses()
                    sync()
                }
                is LoginResult.Failure -> {
                    _loginState.value = LoginState.Error(result.message)
                }
            }
        }
    }

    /** 退出登录。 */
    fun logout() {
        // 先停掉后台同步，避免退出后周期性失败并反复弹通知
        SyncWorker.cancel(getApplication())
        SecurePrefs.clear(getApplication())
        ApiClient.clearSession()
        prefs.loggedIn = false
        _loginState.value = LoginState.LoggedOut
        _courses.value = emptyList()
        _previewTarget.value = null
        _selectedCourseId.value = null
        _syncError.value = null
        _needsReauth.value = false
        _syncProgress.value = ""
    }

    /**
     * 手动同步。
     *
     * [full] 为 true 时强制重下所有未被排除的文件（与桌面端「全量同步」一致）。
     * 与后台 Worker 通过 [SyncGate] 互斥；已有同步在跑时本次调用直接返回。
     */
    fun sync(full: Boolean = false) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncError.value = null
            _syncProgress.value = if (full) "正在全量同步…" else "正在同步…"
            val result = SyncGate.runOrSkip {
                val engine = SyncEngine(getApplication(), api, repo)
                engine.sync(full = full) { _, _, _, message ->
                    _syncProgress.value = message
                }
            }
            _syncing.value = false
            if (result == null) {
                _syncProgress.value = "已有同步正在进行"
                return@launch
            }
            if (result.filesDownloaded > 0) {
                Notifier.notifySyncComplete(
                    getApplication(), result.filesDownloaded, result.bytesDownloaded
                )
            }
            prefs.lastSyncAt = System.currentTimeMillis()
            if (result.needsReauth) {
                // 会话失效：如实告知并退出到登录页，绝不显示「同步完成」
                _needsReauth.value = true
                _syncError.value = "登录状态已失效，请重新登录"
                _syncProgress.value = ""
                logout()
                return@launch
            }
            _syncError.value = result.error
            _syncProgress.value = when {
                result.error != null -> "同步失败：${result.error}"
                result.filesFailed > 0 -> "同步完成，${result.filesFailed} 个文件失败，可重试"
                else -> "同步完成"
            }
            refreshCourses()
        }
    }

    /** 清除错误提示（界面「知道了 / 重试」后调用）。 */
    fun clearSyncError() {
        _syncError.value = null
    }

    /** 上次同步记录（时间与失败原因），用于设置页展示。 */
    fun lastSyncSummary(): String {
        val run = repo.lastRun() ?: return "尚无同步记录"
        val finished = run.finishedAt ?: "未完成"
        val mode = if (run.mode == "full") "全量" else "增量"
        val error = run.error
        return if (error.isNullOrBlank()) {
            "${mode}同步 · $finished · 下载 ${run.filesDownloaded} 个文件"
        } else {
            "${mode}同步 · $finished · 失败：$error"
        }
    }

    /** 刷新课程统计。 */
    fun refreshCourses() {
        _courses.value = repo.courseStats()
        bumpDataVersion()
    }

    /** 选中课程（进入文件列表）。 */
    fun selectCourse(courseId: Long) {
        _selectedCourseId.value = courseId
    }

    /** 返回课程列表。 */
    fun clearCourseSelection() {
        _selectedCourseId.value = null
    }

    /** 获取课程文件。 */
    fun filesOf(courseId: Long): List<FileItem> = repo.getFilesByCourse(courseId)

    /** 更新同步频率。 */
    fun setSyncInterval(minutes: Int) {
        prefs.syncIntervalMinutes = minutes
        SyncWorker.schedule(getApplication(), minutes)
    }

    fun currentInterval(): Int = prefs.syncIntervalMinutes

    /** 删除文件（本地 + 数据库）。 */
    fun deleteFile(file: FileItem) {
        file.localPath.let { path ->
            if (path.isNotEmpty()) {
                val target = java.io.File(path)
                target.delete()
                // 同时清掉可能存在的断点，避免下次同步把半截文件当续传起点
                edu.fudan.elearning.sync.sync.DownloadPlan.partFile(target).delete()
            }
        }
        repo.deleteFile(file.fileId)
        refreshCourses()
    }

    /** 删除课程下所有文件。 */
    fun deleteCourseFiles(courseId: Long) {
        filesOf(courseId).forEach { file ->
            if (file.localPath.isNotEmpty()) {
                val target = java.io.File(file.localPath)
                target.delete()
                edu.fudan.elearning.sync.sync.DownloadPlan.partFile(target).delete()
            }
        }
        repo.deleteFilesByCourse(courseId)
        refreshCourses()
    }

    /**
     * 重试单个失败文件（不触发整轮同步）。
     *
     * 只需要该文件自己的下载 URL 与大小，失败时保留断点，下次可继续续传。
     */
    fun retryFile(file: FileItem) {
        if (file.url.isEmpty()) {
            toast("缺少下载地址，请先执行一次同步")
            return
        }
        viewModelScope.launch {
            val context = getApplication<android.app.Application>()
            val downloader = edu.fudan.elearning.sync.sync.DownloadManager(context)
            val courseDir = downloader.courseDir(repo.courseName(file.courseId))
            val taken = repo.getFilesByCourse(file.courseId)
                .filter { it.fileId != file.fileId && it.localPath.isNotEmpty() }
                .map { java.io.File(it.localPath).name }
                .toSet()
            val dest = file.localPath.takeIf { it.isNotEmpty() }?.let { java.io.File(it) }
                ?: downloader.destinationFor(
                    courseDir,
                    file.filename.ifEmpty {
                        edu.fudan.elearning.sync.sync.DownloadManager.sanitize(file.name)
                    },
                    taken
                )
            when (val outcome = downloader.download(file.url, dest, file.size)) {
                is edu.fudan.elearning.sync.sync.DownloadOutcome.Success -> {
                    repo.upsertFile(
                        file.copy(
                            localPath = outcome.path.absolutePath,
                            size = outcome.bytes,
                            status = "downloaded",
                            downloadedAt = now()
                        )
                    )
                    toast("已重新下载：${file.name}")
                }
                is edu.fudan.elearning.sync.sync.DownloadOutcome.Failed -> {
                    repo.markFailed(file.fileId)
                    toast("重试失败：${outcome.reason}")
                }
            }
            refreshCourses()
        }
    }

    private fun now(): String = java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
    ).format(java.util.Date())

    /** 打开应用内预览（取代旧的 ACTION_VIEW 跳转）。 */
    fun openPreview(file: FileItem) {
        if (file.localPath.isEmpty()) {
            toast("文件尚未下载，请先同步")
            return
        }
        val local = java.io.File(file.localPath)
        if (!local.exists() || !local.isFile) {
            toast("本地文件不存在，可能尚未下载完成或已被删除")
            return
        }
        // 标题用列表显示名（display_name），不用磁盘文件名（可能是百分号编码）
        _previewTarget.value = PreviewTarget(local, file.name.ifEmpty { local.name })
    }

    /** 关闭应用内预览。 */
    fun closePreview() {
        _previewTarget.value = null
    }

    /** 分享文件（系统分享面板，仅临时只读 URI 权限）。 */
    fun shareFile(file: FileItem) {
        FileUtils.shareFile(getApplication(), file)
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(
            getApplication(), message, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCleared() {
        repo.close()
        super.onCleared()
    }
}
