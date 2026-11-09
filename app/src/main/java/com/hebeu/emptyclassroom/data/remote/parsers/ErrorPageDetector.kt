package com.hebeu.emptyclassroom.data.remote.parsers

/** 教务系统统一错误页（HTTP 200 但返回 HTML）的识别。 */
object ErrorPageDetector {

    private val MARKERS = listOf(
        "出错啦",
        "系统运行异常",
        "错误提示",
        "系统维护页面",
        "系统维护中",
    )

    /** 是否是教务系统的 HTML 错误页（而不是登录页）。 */
    fun looksLikeErrorPage(body: String): Boolean {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("<")) return false
        return MARKERS.any { body.contains(it) }
    }
}
