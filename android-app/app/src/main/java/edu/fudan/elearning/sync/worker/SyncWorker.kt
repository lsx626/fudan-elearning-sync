package edu.fudan.elearning.sync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import edu.fudan.elearning.sync.auth.LoginResult
import edu.fudan.elearning.sync.auth.UisAuthenticator
import edu.fudan.elearning.sync.data.Repo
import edu.fudan.elearning.sync.network.ApiClient
import edu.fudan.elearning.sync.network.CanvasApi
import edu.fudan.elearning.sync.sync.SyncEngine
import edu.fudan.elearning.sync.util.Prefs
import edu.fudan.elearning.sync.util.SecurePrefs
import java.util.concurrent.TimeUnit

/** 后台定期同步 Worker。默认每 15 分钟，用户可配置。 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val username = prefs.username
        val password = SecurePrefs.loadPassword(applicationContext)

        if (username.isEmpty() || password.isNullOrEmpty()) {
            return Result.success()
        }

        // 静默登录
        val authResult = UisAuthenticator().login(username, password)
        if (authResult !is LoginResult.Success) {
            return Result.success()
        }
        ApiClient.setSession(authResult.session.canvasSessionCookie, authResult.session.csrfToken)

        // 同步
        val repo = Repo(applicationContext)
        val api = CanvasApi()
        val engine = SyncEngine(applicationContext, api, repo)
        val result = engine.sync(full = false)

        // 下载新文件后发通知
        if (result.filesDownloaded > 0) {
            Notifier.notifySyncComplete(applicationContext, result.filesDownloaded, result.bytesDownloaded)
        }
        prefs.lastSyncAt = System.currentTimeMillis()

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "fudan_sync_periodic_work"

        /** 按用户配置的间隔安排定期同步。 */
        fun schedule(context: Context, intervalMinutes: Int) {
            val minutes = intervalMinutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 立即触发一次同步。 */
        fun syncNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
