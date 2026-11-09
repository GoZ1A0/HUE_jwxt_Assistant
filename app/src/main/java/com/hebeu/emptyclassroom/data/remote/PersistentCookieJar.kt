package com.hebeu.emptyclassroom.data.remote

import com.hebeu.emptyclassroom.data.local.StoredCookie
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp CookieJar：内存保存 + 可导出/导入用于 DataStore 持久化。
 *
 * 教务系统的会话依赖 `JSESSIONID` + `route`（后端粘滞）+ 登录后的 `rememberMe`，
 * 其中 `route` 丢失会被路由到另一节点导致会话不一致，因此必须全部持久化。
 *
 * 线程安全：网络线程与 UI 线程可能同时读写，内部用 synchronized 保护。
 */
class PersistentCookieJar : CookieJar {

    private val lock = Any()
    private var cookies: List<Cookie> = emptyList()

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookies.filter { cookie ->
            cookie.matches(url) &&
                (cookie.expiresAt == Long.MAX_VALUE || cookie.expiresAt > now)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val merged = this.cookies.toMutableList()
            for (cookie in cookies) {
                if (cookie.expiresAt != Long.MAX_VALUE && cookie.expiresAt <= System.currentTimeMillis()) {
                    // 服务端下发的过期 Cookie 等价于删除
                    merged.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                    continue
                }
                merged.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                merged.add(cookie)
            }
            this.cookies = merged
        }
    }

    /** 当前全部未过期 Cookie（用于写入 DataStore）。 */
    fun snapshot(): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookies.filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > now }
    }

    /** 从 DataStore 恢复。 */
    fun restore(stored: List<StoredCookie>) {
        val restored = stored.mapNotNull { it.toCookieOrNull() }
        synchronized(lock) {
            cookies = restored
        }
    }

    fun hasCookie(name: String): Boolean = synchronized(lock) { cookies.any { it.name == name } }

    fun cookieValue(name: String): String? = synchronized(lock) {
        cookies.firstOrNull { it.name == name }?.value
    }

    fun clear() = synchronized(lock) {
        cookies = emptyList()
    }
}

/** Cookie -> 可序列化记录（保留 domain/path/expiry/secure/httpOnly，保证恢复后仍匹配正确站点）。 */
internal fun Cookie.toStoredCookie(): StoredCookie = StoredCookie(
    name = name,
    value = value,
    domain = domain,
    path = path,
    expiresAt = expiresAt,
    secure = secure,
    httpOnly = httpOnly,
    hostOnly = hostOnly,
    persistent = persistent,
)

/** 可序列化记录 -> OkHttp Cookie。损坏记录返回 null（丢弃而不是崩溃）。 */
internal fun StoredCookie.toCookieOrNull(): Cookie? = runCatching {
    val builder = Cookie.Builder()
        .name(name)
        .value(value)
        .path(path)
    if (persistent) builder.expiresAt(expiresAt)
    if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
    if (secure) builder.secure()
    if (httpOnly) builder.httpOnly()
    builder.build()
}.getOrNull()
