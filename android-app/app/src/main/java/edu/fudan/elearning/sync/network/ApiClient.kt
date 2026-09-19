package edu.fudan.elearning.sync.network

import edu.fudan.elearning.sync.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Canvas API 客户端：统一管理会话 Cookie 与请求。 */
object ApiClient {
    var canvasSession: String = ""
    var csrfToken: String = ""

    /**
     * 会话 Cookie 名。
     *
     * Canvas 在不同部署/登录路径下会下发 `_normandy_session` 或 `_canvas_session`，
     * 只认前者会让部分账号登录成功后仍被判为未登录。
     */
    var sessionCookieName: String = COOKIE_NORMANDY

    const val COOKIE_NORMANDY = "_normandy_session"
    const val COOKIE_CANVAS = "_canvas_session"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) FuXiaoXue/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
            if (canvasSession.isNotEmpty()) {
                builder.header("Cookie", "$sessionCookieName=$canvasSession")
            }
            if (csrfToken.isNotEmpty() &&
                (original.method == "POST" || original.method == "PUT" || original.method == "DELETE")
            ) {
                builder.header("X-CSRF-Token", csrfToken)
            }
            chain.proceed(builder.build())
        }
        .build()

    fun setSession(session: String, csrf: String?, cookieName: String = COOKIE_NORMANDY) {
        canvasSession = session
        csrfToken = csrf ?: ""
        sessionCookieName = cookieName.ifEmpty { COOKIE_NORMANDY }
    }

    fun clearSession() {
        canvasSession = ""
        csrfToken = ""
        sessionCookieName = COOKIE_NORMANDY
    }

    fun get(url: String): okhttp3.Response =
        client.newCall(Request.Builder().url(url).get().build()).execute()

    /**
     * 下载文件内容。
     *
     * 必须复用带会话 Cookie 的同一个 client（[client]），否则 Canvas 的签名下载
     * 链接会 302 到 UIS 登录页，表现为「下载成功但内容是登录页」。
     * [rangeHeader] 用于断点续传（如 `bytes=1024-`）。
     */
    fun download(url: String, rangeHeader: String? = null): okhttp3.Response {
        val builder = Request.Builder().url(url).get()
        if (!rangeHeader.isNullOrBlank()) builder.header("Range", rangeHeader)
        return client.newCall(builder.build()).execute()
    }
}
