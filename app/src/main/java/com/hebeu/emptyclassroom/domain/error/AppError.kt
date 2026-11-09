package com.hebeu.emptyclassroom.domain.error

/**
 * 数据层统一抛出的领域错误。UI 层只负责把类型映射为文案，不应解析 message。
 *
 * 与 `docs/design/architecture.md` §4 完全一致。
 */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    /** 连接失败 / DNS 解析失败 / 断网 / 连接被重置 */
    class Network(cause: Throwable? = null) : AppError(cause = cause)

    /** 连接超时或读取超时（SocketTimeout） */
    class Timeout(cause: Throwable? = null) : AppError(cause = cause)

    /** 登录失败：账号或密码错误。serverMessage 为教务系统真实返回的文案 */
    class InvalidCredentials(val serverMessage: String?) : AppError(serverMessage)

    /** 登录需要验证码（当前部署未出现，预留） */
    class CaptchaRequired(val imageUrl: String) : AppError()

    /** 会话失效：需要重新登录（HTTP 901，或页面被 302 重定向到登录页） */
    class SessionExpired : AppError()

    /** 服务端错误（HTTP 5xx 或业务错误页） */
    class Server(val httpCode: Int?, val serverMessage: String? = null, cause: Throwable? = null) : AppError(serverMessage, cause)

    /** 响应不是预期格式 / JSON 或 HTML 解析失败 */
    class Parse(cause: Throwable? = null) : AppError(cause = cause)

    /** 该学年学期场地预约未开放（预检接口返回 "0"） */
    class TermClosed(message: String? = null) : AppError(message)
}
