package edu.fudan.elearning.sync.network

import edu.fudan.elearning.sync.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Canvas API 客户端：统一管理会话 Cookie 与请求。 */
object ApiClient {
    var canvasSession: String = ""
    var csrfToken: String = ""

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
                builder.header("Cookie", "_normandy_session=$canvasSession")
            }
            if (csrfToken.isNotEmpty() &&
                (original.method == "POST" || original.method == "PUT" || original.method == "DELETE")
            ) {
                builder.header("X-CSRF-Token", csrfToken)
            }
            chain.proceed(builder.build())
        }
        .build()

    fun setSession(session: String, csrf: String?) {
        canvasSession = session
        csrfToken = csrf ?: ""
    }

    fun clearSession() {
        canvasSession = ""
        csrfToken = ""
    }

    fun get(url: String): okhttp3.Response =
        client.newCall(Request.Builder().url(url).get().build()).execute()

    fun download(url: String): okhttp3.Response =
        client.newCall(Request.Builder().url(url).get().build()).execute()
}
