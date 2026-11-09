package com.hebeu.emptyclassroom.ui.login

/**
 * 登录页 UI 状态（architecture.md §5：username / password / isPasswordVisible / isSubmitting / errorText）。
 *
 * 注意：密码只保存在内存中的临时状态里，登录成功后立即清空，绝不写入 DataStore（architecture.md §6）。
 */
data class LoginUiState(
    val autoLogin: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val errorText: String? = null,
) {
    /** 登录按钮是否可点击：仅在有输入且未提交时可用（空值由提交时行内提示兜底）。 */
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.isNotEmpty()
}

/** 一次性事件：登录页不需要导航（AppRoot 观察 session），保留 channel 供测试/埋点。 */
sealed interface LoginEvent {
    data class LoggedIn(val username: String) : LoginEvent
}
