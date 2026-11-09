package com.hebeu.emptyclassroom.domain.repository

import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.model.LoginOutcome
import com.hebeu.emptyclassroom.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * 登录/会话仓库。Cookie 本机加密保存；仅主动开启自动登录时加密保存密码。
 *
 * 会话失效（HTTP 901 / 302 到登录页）时，实现类会把 [session] 置为
 * [SessionState.Expired] 并抛出 [AppError.SessionExpired]。
 */
interface AuthRepository {

    val session: StateFlow<SessionState>

    /**
     * 启动时恢复本地会话：读取 DataStore 中的 Cookie 并请求 `/xtgl/index_initMenu.html` 轻量校验。
     * @return true=可直接进入查询页；false=需要登录。
     */
    suspend fun restoreSession(): Boolean
    /** 只读取本地登录标记，不进行校验或网络请求。 */
    suspend fun hasSavedLogin(): Boolean = false

    /** 原生 RSA 登录。失败返回 [LoginOutcome.Failure]，不抛异常。 */
    suspend fun login(username: String, password: String): LoginOutcome

    suspend fun login(username: String, password: String, autoLogin: Boolean): LoginOutcome = login(username, password)
    suspend fun isAutoLoginEnabled(): Boolean = false
    suspend fun disableAutoLogin() {}

    /** 退出登录：通知服务端（尽力而为）并清空本机会话与 Cookie。 */
    suspend fun logout()
}
