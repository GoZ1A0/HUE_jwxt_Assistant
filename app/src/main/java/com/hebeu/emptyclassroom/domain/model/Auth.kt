package com.hebeu.emptyclassroom.domain.model

import com.hebeu.emptyclassroom.domain.error.AppError

/** 当前登录态。UI 的 AppRoot 观察它决定显示登录页还是查询页。 */
sealed interface SessionState {
    data object LoggedOut : SessionState
    data class LoggedIn(val username: String) : SessionState
    data class Expired(val reason: String? = null) : SessionState
}

/** 登录结果（不抛异常，便于 ViewModel 直接映射状态）。 */
sealed interface LoginOutcome {
    data object Success : LoginOutcome
    data class Failure(val error: AppError) : LoginOutcome
}
