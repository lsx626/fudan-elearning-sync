package edu.fudan.elearning.sync.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** 内存 Cookie 容器，供登录会话复用。 */
class SessionCookieJar : CookieJar {
    val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { new ->
            val idx = this.cookies.indexOfFirst { it.name == new.name }
            if (idx >= 0) this.cookies[idx] = new else this.cookies.add(new)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.toList()

    fun clear() = cookies.clear()
}
