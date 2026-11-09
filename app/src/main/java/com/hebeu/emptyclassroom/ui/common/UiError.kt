package com.hebeu.emptyclassroom.ui.common

import com.hebeu.emptyclassroom.domain.error.AppError

/** 结果区错误的粗分类：决定图标、文案与是否给「重试」。 */
enum class ErrorKind {
    NETWORK,
    TIMEOUT,
    SERVER,
    PARSE,
    TERM_CLOSED,
    SESSION_EXPIRED,
    OTHER,
}

/**
 * UI 展示用的错误模型。文案集中在这里，data 层只抛 [AppError]，不负责文案（architecture.md §3）。
 * `title` 用于状态面板标题，`message` 用于副文案；两者都展示，保证「文字 + 颜色」双通道。
 */
data class UiError(
    val kind: ErrorKind,
    val title: String,
    val message: String,
    val retryable: Boolean = true,
)

object AppErrorText {
    const val NETWORK = "网络连接失败，请检查网络后重试"
    const val TIMEOUT = "请求超时，请重试"
    const val SERVER = "教务系统暂时不可用，请稍后重试"
    const val PARSE = "数据解析失败，请稍后重试"
    const val INVALID_CREDENTIALS = "账号或密码错误"
    const val CAPTCHA = "教务系统要求输入验证码，当前版本暂不支持，请稍后重试"
    const val SESSION_EXPIRED = "登录已过期，请重新登录"
    const val TERM_CLOSED = "该学年学期场地预约尚未开放，请选择其他学期"
    const val UNKNOWN = "出现未知问题，请稍后重试"

    /** 把领域错误映射为展示模型；对未知 Throwable 兜底为「未知问题」。 */
    fun toUiError(throwable: Throwable): UiError = when (throwable) {
        is AppError.Network -> UiError(
            kind = ErrorKind.NETWORK,
            title = "网络连接失败",
            message = NETWORK,
        )
        is AppError.Timeout -> UiError(
            kind = ErrorKind.TIMEOUT,
            title = "请求超时",
            message = TIMEOUT,
        )
        is AppError.Server -> UiError(
            kind = ErrorKind.SERVER,
            title = "教务系统暂时不可用",
            message = throwable.serverMessage?.takeIf { it.isNotBlank() } ?: SERVER,
        )
        is AppError.Parse -> UiError(
            kind = ErrorKind.PARSE,
            title = "数据解析失败",
            message = PARSE,
        )
        is AppError.TermClosed -> UiError(
            kind = ErrorKind.TERM_CLOSED,
            title = "场地预约未开放",
            message = throwable.message?.takeIf { it.isNotBlank() } ?: TERM_CLOSED,
            retryable = false,
        )
        is AppError.SessionExpired -> UiError(
            kind = ErrorKind.SESSION_EXPIRED,
            title = "登录已过期",
            message = SESSION_EXPIRED,
            retryable = false,
        )
        is AppError.InvalidCredentials -> UiError(
            kind = ErrorKind.OTHER,
            title = "登录失败",
            message = throwable.serverMessage?.takeIf { it.isNotBlank() } ?: INVALID_CREDENTIALS,
            retryable = false,
        )
        is AppError.CaptchaRequired -> UiError(
            kind = ErrorKind.OTHER,
            title = "需要验证码",
            message = CAPTCHA,
            retryable = false,
        )
        else -> UiError(
            kind = ErrorKind.OTHER,
            title = "查询失败",
            message = throwable.message?.takeIf { it.isNotBlank() } ?: UNKNOWN,
        )
    }

    /** 登录页错误文案（单行，展示在 InlineErrorCard 中）。 */
    fun loginMessage(throwable: Throwable): String = when (throwable) {
        is AppError.InvalidCredentials -> throwable.serverMessage?.takeIf { it.isNotBlank() } ?: INVALID_CREDENTIALS
        is AppError.CaptchaRequired -> CAPTCHA
        is AppError.Network -> NETWORK
        is AppError.Timeout -> TIMEOUT
        is AppError.Server -> throwable.serverMessage?.takeIf { it.isNotBlank() } ?: SERVER
        is AppError.Parse -> PARSE
        is AppError.SessionExpired -> SESSION_EXPIRED
        is AppError.TermClosed -> throwable.message?.takeIf { it.isNotBlank() } ?: TERM_CLOSED
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: UNKNOWN
    }
}
