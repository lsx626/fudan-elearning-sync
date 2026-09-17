package edu.fudan.elearning.sync

import android.app.Application
import edu.fudan.elearning.sync.worker.Notifier

/** 应用入口：初始化通知渠道。 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
    }
}
