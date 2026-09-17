package edu.fudan.elearning.sync.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** 内存 Cookie 容器，供登录会话复用（按域正确隔离）。 */
class SessionCookieJar : CookieJar {
    val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { new ->
            // 按 name + domain 去重，避免不同域的同名 Cookie 互相覆盖
            val idx = this.cookies.indexOfFirst {
                it.name == new.name && it.domain == new.domain
            }
            if (idx >= 0) this.cookies[idx] = new else this.cookies.add(new)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        // 只返回与该 URL 域/路径匹配的 Cookie
        cookies.filter { it.matches(url) }

    fun clear() = cookies.clear()
}
