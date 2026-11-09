package com.hebeu.emptyclassroom.data.remote

import com.hebeu.emptyclassroom.data.remote.parsers.LoginPageData
import com.hebeu.emptyclassroom.data.remote.parsers.LoginPageParser
import com.hebeu.emptyclassroom.data.remote.parsers.jsonText
import com.hebeu.emptyclassroom.data.remote.parsers.parseJsonObjectOrNull
import com.hebeu.emptyclassroom.domain.error.AppError

/** 服务端下发的 RSA 公钥（原始 base64 字符串）。 */
data class RsaPublicKey(
    val modulusBase64: String,
    val exponentBase64: String,
)

/**
 * 原生登录 API（契约 §2）：
 *
 * ```
 * GET  /xtgl/login_slogin.html                  → 解析 hidden 域 + csrftoken
 * GET  /xtgl/login_getPublicKey.html?time=<ms>  → {"modulus","exponent"}（base64）
 * POST /xtgl/login_slogin.html?time=<ms>        → 全部 hidden 域 + yhm + mm(RSA 密文)
 * ```
 *
 * 成功：HTTP 302（+ rememberMe Cookie），用 `GET /xtgl/index_initMenu.html` 复核；
 * 失败：HTTP 200 + 登录页 HTML，含「用户名或密码不正确，请重新输入！」。
 */
class ZfLoginApi(
    private val http: ZfHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun login(username: String, password: String) {
        // 显式登录：先丢弃旧会话 Cookie，保证 csrftoken 与 JSESSIONID 来自同一次匿名会话
        http.clearCookies()
        val html = fetchLoginPageHtml()
        val page = LoginPageParser.parse(html, http.baseUrl.toString())
        if (page.csrfToken == null) {
            // 既不是登录页（可能已有有效会话，例如上次登录 Cookie 仍有效）
            if (verifySession()) return
            throw AppError.Parse(IllegalStateException("login page has no csrftoken"))
        }
        val publicKey = fetchPublicKey()
        submitLogin(page, username, password, publicKey)
        // 登录成功的最终裁决：菜单页可访问
        if (!verifySession()) {
            throw AppError.Server(null, "登录后校验会话失败")
        }
    }

    suspend fun fetchLoginPageHtml(): String {
        val result = http.get(
            url = http.url(LOGIN_PAGE_PATH),
            headers = mapOf("Referer" to http.url(LOGIN_PAGE_PATH).toString()),
        )
        if (result.code == ZfHttpClient.HTTP_SESSION_EXPIRED) throw AppError.SessionExpired()
        if (!result.isSuccessful) throw AppError.Server(result.code, result.bodySnippet())
        if (result.body.isBlank()) throw AppError.Parse(IllegalStateException("empty login page"))
        return result.body
    }

    suspend fun fetchPublicKey(): RsaPublicKey {
        val result = http.get(
            url = http.url(PUBLIC_KEY_PATH, mapOf("time" to clock().toString())),
            headers = xhrHeaders(accept = "application/json, text/javascript, */*; q=0.01"),
        )
        if (result.code == ZfHttpClient.HTTP_SESSION_EXPIRED) throw AppError.SessionExpired()
        if (!result.isSuccessful) throw AppError.Server(result.code, result.bodySnippet())
        val json = parseJsonObjectOrNull(result.body)
            ?: throw AppError.Parse(IllegalStateException("public key is not JSON"))
        val modulus = json.jsonText("modulus")
            ?: throw AppError.Parse(IllegalStateException("public key has no modulus"))
        val exponent = json.jsonText("exponent") ?: DEFAULT_EXPONENT
        return RsaPublicKey(modulusBase64 = modulus, exponentBase64 = exponent)
    }

    /** 用登录页 hidden 域 + 账号 + RSA 密文提交登录；按契约判定成功/失败。 */
    private suspend fun submitLogin(
        loginPage: LoginPageData,
        username: String,
        password: String,
        publicKey: RsaPublicKey,
    ) {
        val form = LinkedHashMap<String, String>(loginPage.hiddenFields.size + 2)
        form.putAll(loginPage.hiddenFields)
        form["yhm"] = username
        // 密码只在本函数内以密文出现，不写日志、不持久化
        form["mm"] = RsaCrypto.encryptPassword(password, publicKey.modulusBase64, publicKey.exponentBase64)

        val result = http.postForm(
            url = http.url(LOGIN_PAGE_PATH, mapOf("time" to clock().toString())),
            form = form,
            headers = mapOf(
                "Referer" to http.url(LOGIN_PAGE_PATH).toString(),
                "Origin" to origin(),
            ),
            followRedirects = false,
        )

        when {
            result.code == ZfHttpClient.HTTP_SESSION_EXPIRED -> throw AppError.SessionExpired()
            result.code in 300..399 -> Unit // 成功信号：302；随后由 verifySession 复核
            result.code in 500..599 -> throw AppError.Server(result.code, result.bodySnippet())
            result.code == 200 -> handleLoginFailurePage(result)
            else -> throw AppError.Server(result.code, result.bodySnippet())
        }
    }

    private fun handleLoginFailurePage(result: HttpResult) {
        val html = result.body
        if (LoginPageParser.looksLikeLoginPage(html) || html.contains("csrftoken")) {
            LoginPageParser.captchaImageUrl(html, http.baseUrl.toString())?.let { imageUrl ->
                throw AppError.CaptchaRequired(imageUrl)
            }
            throw AppError.InvalidCredentials(LoginPageParser.failureMessage(html))
        }
        // HTTP 200 但不是登录页：极少见（无重定向直接渲染首页），交由调用方 verifySession 判定
    }

    /**
     * 轻量会话校验：`GET /xtgl/index_initMenu.html`。
     * 返回 false 仅表示「未登录/已过期」；网络错误继续抛出，由调用方决定是否乐观放行。
     *
     * 直接 GET 菜单页是契约 §3 的记录方式；若菜单页判定失败，再按契约 §2.2 的浏览器路径
     * （`GET /xtgl/login_slogin.html` 会 302 到菜单页）兜底确认一次，避免误判已登录会话过期。
     */
    suspend fun verifySession(): Boolean {
        val menu = http.get(
            url = http.url(MENU_PATH),
            headers = mapOf("Referer" to http.url(MENU_PATH).toString()),
        )
        if (isValidMenuPage(menu)) return true
        // 已经是明确的「未登录/过期」或服务端异常，无需再兜底
        if (menu.code == ZfHttpClient.HTTP_SESSION_EXPIRED) return false
        if (menu.redirectedToLogin) return false
        if (!menu.isSuccessful) throw AppError.Server(menu.code)
        // 仅「200 但页面内容可疑」时兜底：GET 登录页，已登录时应 302 到 /xtgl/index_initMenu.html（契约 §2.2）
        val viaLoginPage = http.get(
            url = http.url(LOGIN_PAGE_PATH),
            headers = mapOf("Referer" to http.url(LOGIN_PAGE_PATH).toString()),
        )
        if (viaLoginPage.code == ZfHttpClient.HTTP_SESSION_EXPIRED) return false
        if (!viaLoginPage.isSuccessful) throw AppError.Server(viaLoginPage.code)
        return viaLoginPage.isSuccessful &&
            viaLoginPage.finalUrl.contains(MENU_PAGE_HINT, ignoreCase = true)
    }

    private fun isValidMenuPage(result: HttpResult): Boolean {
        if (result.code == ZfHttpClient.HTTP_SESSION_EXPIRED) return false
        if (result.redirectedToLogin) return false
        if (!result.isSuccessful) return false
        val html = result.body
        // 登录页特征（会话失效时页面会被重定向回登录页）
        if (html.contains("csrftoken") && html.contains("login_slogin") && html.contains("name=\"mm\"")) {
            return false
        }
        // 菜单页至少几十 KB，过短视为异常页
        return html.length > MIN_MENU_PAGE_LENGTH
    }

    /** 退出登录：尽力通知服务端，然后清空全部 Cookie。 */
    suspend fun logout() {
        runCatching {
            http.postForm(
                url = http.url(LOGOUT_PATH),
                form = emptyMap(),
                headers = mapOf("Referer" to http.url(MENU_PATH).toString(), "Origin" to origin()),
                followRedirects = true,
            )
        }
        http.clearCookies()
    }

    private fun xhrHeaders(accept: String): Map<String, String> = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to accept,
        "Referer" to http.url(LOGIN_PAGE_PATH).toString(),
        "Origin" to origin(),
    )

    private fun origin(): String = "${http.baseUrl.scheme}://${http.baseUrl.host}"

    private companion object {
        const val LOGIN_PAGE_PATH = "xtgl/login_slogin.html"
        const val PUBLIC_KEY_PATH = "xtgl/login_getPublicKey.html"
        const val MENU_PATH = "xtgl/index_initMenu.html"
        const val MENU_PAGE_HINT = "xtgl/index_initMenu"
        const val LOGOUT_PATH = "xtgl/login_logoutAccount.html"
        const val DEFAULT_EXPONENT = "AQAB"
        const val MIN_MENU_PAGE_LENGTH = 500
    }
}
