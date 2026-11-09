package com.hebeu.emptyclassroom.data.remote

import com.hebeu.emptyclassroom.data.local.SessionStore
import com.hebeu.emptyclassroom.domain.error.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 一次 HTTP 请求的结果（已读取完 body）。 */
data class HttpResult(
    val code: Int,
    val finalUrl: String,
    val body: String,
    val contentType: String?,
    /** 是否在重定向链中出现「跳到登录页」或最终落在登录页——用于会话失效判定。 */
    val redirectedToLogin: Boolean,
    val setCookieNames: Set<String>,
) {
    val isSuccessful: Boolean get() = code in 200..299

    val isJson: Boolean get() =
        contentType?.contains("json", ignoreCase = true) == true ||
            body.trimStart().startsWith("{") || body.trimStart().startsWith("[")

    /** 仅用于错误诊断的短片段，绝不含 Cookie/凭据。 */
    fun bodySnippet(maxLength: Int = 120): String =
        body.trim().replace(Regex("\\s+"), " ").take(maxLength)

    /** 真实的登录页 HTML（不是重定向后的目标页）。 */
    val looksLikeLoginPage: Boolean
        get() = body.contains("login_slogin") && body.contains("csrftoken")
}

/**
 * 教务系统 HTTP 客户端：OkHttp + 持久化 CookieJar + 统一请求头 + 超时 + 可取消。
 *
 * 设计要点（见 docs/recon/site-contract.md）：
 * - 桌面 Chrome UA + `Accept-Language: zh-CN`（站点有浏览器判定，桌面 UA 最稳）
 * - 连接 10s / 读 15s / 写 15s / 整次调用 30s
 * - 会话失效两种表现都在这里统一识别：HTTP **901**（XHR 请求）与 **302 到登录页**（页面 GET）
 * - 每次请求后把 Cookie 快照写入 [SessionStore]（若提供），实现跨进程会话恢复
 * - 不打印任何请求/响应内容
 */
class ZfHttpClient(
    val baseUrl: HttpUrl = BASE_URL,
    val cookieJar: PersistentCookieJar = PersistentCookieJar(),
    private val sessionStore: SessionStore? = null,
    baseClient: OkHttpClient? = null,
) {

    private val client: OkHttpClient = (baseClient ?: defaultClient())
        .newBuilder()
        // 关键：CookieJar 必须挂在 OkHttpClient 上，否则 JSESSIONID/route/rememberMe 不会收发
        .cookieJar(cookieJar)
        .build()
    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** 构造站点内绝对 URL。 */
    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = baseUrl.resolve(path)?.newBuilder()
            ?: throw IllegalArgumentException("bad path: $path")
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    suspend fun get(
        url: HttpUrl,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): HttpResult {
        val request = Request.Builder()
            .url(url)
            .get()
            .applyBrowserHeaders(headers)
            .build()
        return execute(request, followRedirects)
    }

    /** form-urlencoded POST。默认不跟随重定向，便于直接观察登录成功（302）或会话失效（302 到登录页）。 */
    suspend fun postForm(
        url: HttpUrl,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = false,
    ): HttpResult {
        val request = Request.Builder()
            .url(url)
            .post(encodeForm(form))
            .applyBrowserHeaders(headers)
            .build()
        return execute(request, followRedirects)
    }

    /** 把当前 Cookie 快照写入 DataStore。 */
    suspend fun persistCookies() {
        val store = sessionStore ?: return
        val snapshot = cookieJar.snapshot().map { it.toStoredCookie() }
        store.saveCookies(snapshot)
    }

    /** 从 DataStore 恢复 Cookie；返回是否恢复出至少一条。 */
    suspend fun restoreCookies(): Boolean {
        val store = sessionStore ?: return false
        val stored = store.loadCookies()
        cookieJar.restore(stored)
        // 清理已过期项，避免把过期 Cookie 当作有效会话
        return cookieJar.snapshot().isNotEmpty()
    }

    /** 清空内存与持久化 Cookie。 */
    suspend fun clearCookies() {
        cookieJar.clear()
        sessionStore?.saveCookies(emptyList())
    }

    fun cancelCalls() { client.dispatcher.cancelAll() }

    private suspend fun execute(request: Request, followRedirects: Boolean): HttpResult =
        withContext(Dispatchers.IO) {
            val httpClient = if (followRedirects) client else noRedirectClient
            try {
                val response: Response = httpClient.newCall(request).await()
                response.use { httpResponse ->
                    // 读 body 也可能超时（服务端延迟响应），因此整个读写过程都在错误映射范围内
                    val bodyText = httpResponse.body?.string().orEmpty()
                    val finalUrl = httpResponse.request.url.toString()
                    val redirectedToLogin = hasLoginRedirect(httpResponse) ||
                        finalUrl.contains(LOGIN_PATH, ignoreCase = true)
                    val cookieNames = httpResponse.headers.values("Set-Cookie")
                        .mapNotNull { header -> header.substringBefore('=').trim().ifBlank { null } }
                        .toSet()
                    val result = HttpResult(
                        code = httpResponse.code,
                        finalUrl = finalUrl,
                        body = bodyText,
                        contentType = httpResponse.header("Content-Type"),
                        redirectedToLogin = redirectedToLogin,
                        setCookieNames = cookieNames,
                    )
                    persistCookies()
                    result
                }
            } catch (e: SocketTimeoutException) {
                throw AppError.Timeout(e)
            } catch (e: InterruptedIOException) {
                throw AppError.Timeout(e)
            } catch (e: IOException) {
                throw AppError.Network(e)
            }
        }

    private fun Request.Builder.applyBrowserHeaders(extra: Map<String, String>): Request.Builder {
        header("User-Agent", USER_AGENT)
        header("Accept-Language", "zh-CN,zh;q=0.9")
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        extra.forEach { (name, value) -> header(name, value) }
        return this
    }

    private fun encodeForm(form: Map<String, String>): okhttp3.RequestBody {
        val encoded = form.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return encoded.toRequestBody(FORM_MEDIA_TYPE)
    }

    private fun hasLoginRedirect(response: Response): Boolean {
        var current: Response? = response
        while (current != null) {
            val code = current.code
            if (code in 300..399) {
                val location = current.header("Location").orEmpty()
                if (location.contains(LOGIN_PATH, ignoreCase = true)) return true
            }
            current = current.priorResponse
        }
        return false
    }

    companion object {
        /** 教务系统站点根。 */
        const val HOST: String = "jwglxxfwpt.hebeu.edu.cn"
        val BASE_URL: HttpUrl = "https://$HOST/".toHttpUrl()

        /** 桌面 Chrome UA（与真实浏览器一致，站点有前端浏览器判定）。 */
        const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        const val LOGIN_PATH: String = "login_slogin"

        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 15L
        private const val WRITE_TIMEOUT_SECONDS = 15L
        private const val CALL_TIMEOUT_SECONDS = 30L

        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** 登录成功/失败判定的原始形态，单独暴露便于测试。 */
        fun isSessionExpired(result: HttpResult): Boolean =
            result.code == HTTP_SESSION_EXPIRED || result.redirectedToLogin

        const val HTTP_SESSION_EXPIRED: Int = 901
    }
}

/** 可取消的 OkHttp 调用封装：协程取消时同步 cancel 底层 Call。 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) {
                response.close()
                return
            }
            continuation.resume(response)
        }
    })
}
