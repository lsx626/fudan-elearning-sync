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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 登录状态。 */
sealed class LoginState {
    object Loading : LoginState()
    data class LoggedIn(val username: String) : LoginState()
    object LoggedOut : LoginState()
    data class Error(val message: String) : LoginState()
}

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

    /** 应用内预览目标文件（null 表示不在预览态）。 */
    private val _previewFile = MutableStateFlow<java.io.File?>(null)
    val previewFile: StateFlow<java.io.File?> = _previewFile

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
                    ApiClient.setSession(result.session.canvasSessionCookie, result.session.csrfToken)
                    prefs.username = username
                    prefs.loggedIn = true
                    if (remember) {
                        SecurePrefs.savePassword(getApplication(), password)
                    }
                    _loginState.value = LoginState.LoggedIn(username)
                    // 安排后台定期同步
                    SyncWorker.schedule(getApplication(), prefs.syncIntervalMinutes)
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
        SecurePrefs.clear(getApplication())
        ApiClient.clearSession()
        prefs.loggedIn = false
        _loginState.value = LoginState.LoggedOut
        _courses.value = emptyList()
        _previewFile.value = null
    }

    /** 手动同步。 */
    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _syncProgress.value = "正在同步…"
            val engine = SyncEngine(getApplication(), api, repo)
            val result = engine.sync(full = false) { _, _, _, message ->
                _syncProgress.value = message
            }
            _syncing.value = false
            _syncProgress.value = "同步完成"
            if (result.filesDownloaded > 0) {
                Notifier.notifySyncComplete(
                    getApplication(), result.filesDownloaded, result.bytesDownloaded
                )
            }
            prefs.lastSyncAt = System.currentTimeMillis()
            refreshCourses()
        }
    }

    /** 刷新课程统计。 */
    fun refreshCourses() {
        _courses.value = repo.courseStats()
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
                java.io.File(path).delete()
            }
        }
        repo.deleteFile(file.fileId)
        refreshCourses()
    }

    /** 删除课程下所有文件。 */
    fun deleteCourseFiles(courseId: Long) {
        filesOf(courseId).forEach { file ->
            if (file.localPath.isNotEmpty()) java.io.File(file.localPath).delete()
        }
        repo.deleteFilesByCourse(courseId)
        refreshCourses()
    }

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
        _previewFile.value = local
    }

    /** 关闭应用内预览。 */
    fun closePreview() {
        _previewFile.value = null
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