package com.hebeu.emptyclassroom.data.remote.parsers

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** 登录页解析结果：hidden 域（原样回传）+ CSRF token + 验证码图片（若出现）。 */
data class LoginPageData(
    val hiddenFields: Map<String, String>,
    val csrfToken: String?,
    val captchaImageUrl: String?,
)

/**
 * 登录页 `/xtgl/login_slogin.html` 解析。
 *
 * 关键点（契约 §2.1/§2.3）：
 * - POST 时必须原样携带页面全部 hidden 域（尤其 csrftoken / language / mmsfjm / xxdm）；
 * - 页面每次 GET 都会生成新的 csrftoken，必须与 JSESSIONID 同会话；
 * - 当前部署没有验证码控件，但保留识别能力（若服务端强制，返回图片地址）；
 * - 失败页是 HTTP 200 的登录页 HTML，用文案识别。
 */
object LoginPageParser {

    /** 登录失败时服务端渲染的文案（实测为第一条，其余为防御性扩展）。 */
    private val FAILURE_KEYWORDS = listOf(
        "用户名或密码不正确，请重新输入！",
        "用户名或密码不正确",
        "用户名或密码错误",
        "用户不存在",
        "密码不正确",
        "密码错误",
        "账号已被锁定",
        "登录过于频繁",
    )

    private val LOGIN_FORM_SELECTOR = "form[action*=login_slogin]"

    fun parse(html: String, baseUrl: String): LoginPageData {
        val document = Jsoup.parse(html, baseUrl)
        val form = document.selectFirst(LOGIN_FORM_SELECTOR) ?: document.selectFirst("form")
        val hidden = LinkedHashMap<String, String>()
        // 先取登录表单内的 hidden 域（csrftoken/language/ydType ...）
        form?.select("input[type=hidden][name]")?.forEach { input ->
            val name = input.attr("name").trim()
            if (name.isNotEmpty()) hidden[name] = input.attr("value")
        }
        // 真实页面有 15+ 个 hidden 域（mmsfjm/xxdm/yzcskz/dlsbsdsj ...）直接渲染在 <body> 下、
        // 不在 form 内（实测如此，也与 recon 脚本「页面全部 hidden 域」一致），需要一并回传。
        document.select("input[type=hidden][name]").forEach { input ->
            val name = input.attr("name").trim()
            if (name.isNotEmpty() && !hidden.containsKey(name)) hidden[name] = input.attr("value")
        }
        return LoginPageData(
            hiddenFields = hidden,
            csrfToken = hidden["csrftoken"]?.takeIf { it.isNotBlank() },
            captchaImageUrl = captchaImageUrl(document, baseUrl),
        )
    }

    /**
     * 识别登录失败文案；不是失败页时返回 null。
     * 只读取页面可见文本，不读取任何输入框内容。
     */
    fun failureMessage(html: String): String? {
        val document = Jsoup.parse(html)
        val bodyText = document.body()?.text().orEmpty()
        FAILURE_KEYWORDS.firstOrNull { bodyText.contains(it) }?.let { return it }
        val container = document.selectFirst(
            ".alert-danger, .alert, .text-danger, .error, #msg, #errorMsg, .bootbox-body",
        )
        val text = container?.text()?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() && it.length <= 120 }
    }

    /** 页面是否是登录页（有登录表单 + csrftoken）。 */
    fun looksLikeLoginPage(html: String): Boolean =
        html.contains("login_slogin") && html.contains("csrftoken") &&
            (html.contains("name=\"mm\"") || html.contains("name='mm'"))

    /** 验证码控件存在时返回图片绝对 URL（当前部署不存在）。 */
    fun captchaImageUrl(html: String, baseUrl: String): String? =
        captchaImageUrl(Jsoup.parse(html, baseUrl), baseUrl)

    private fun captchaImageUrl(document: Document, baseUrl: String): String? {
        val image = document.selectFirst(
            "img[src*=kaptcha], img#yzmPic, img#yzm, #yzmPic img, input[name=yzm]",
        ) ?: return null
        val source = image.tagName().let { tag ->
            if (tag == "img") image.attr("src") else image.attr("value")
        }
        if (source.isBlank()) return null
        if (source.startsWith("http://") || source.startsWith("https://")) return source
        if (source.startsWith("//")) return "https:$source"
        return baseUrl.trimEnd('/') + "/" + source.trimStart('/')
    }
}
