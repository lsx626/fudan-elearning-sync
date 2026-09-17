package edu.fudan.elearning.sync.util

import android.content.Context
import android.content.SharedPreferences

/** 应用配置存取（非敏感项）。 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("fudan_sync", Context.MODE_PRIVATE)

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(value) = sp.edit().putString("username", value).apply()

    var syncIntervalMinutes: Int
        get() = sp.getInt("sync_interval_minutes", 15)
        set(value) = sp.edit().putInt("sync_interval_minutes", value).apply()

    var onlyFavorites: Boolean
        get() = sp.getBoolean("only_favorites", false)
        set(value) = sp.edit().putBoolean("only_favorites", value).apply()

    var loggedIn: Boolean
        get() = sp.getBoolean("logged_in", false)
        set(value) = sp.edit().putBoolean("logged_in", value).apply()

    var lastSyncAt: Long
        get() = sp.getLong("last_sync_at", 0)
        set(value) = sp.edit().putLong("last_sync_at", value).apply()

    fun clear() {
        sp.edit().clear().apply()
    }
}
