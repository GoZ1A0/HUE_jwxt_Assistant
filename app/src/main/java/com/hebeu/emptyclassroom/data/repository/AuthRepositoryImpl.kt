package com.hebeu.emptyclassroom.data.repository

import com.hebeu.emptyclassroom.data.local.PreferencesStore
import com.hebeu.emptyclassroom.data.local.SessionStore
import com.hebeu.emptyclassroom.data.local.CredentialStore
import com.hebeu.emptyclassroom.data.local.SavedCredentials
import com.hebeu.emptyclassroom.data.session.SessionGuard
import com.hebeu.emptyclassroom.data.remote.ZfHttpClient
import com.hebeu.emptyclassroom.data.remote.ZfLoginApi
import com.hebeu.emptyclassroom.data.remote.toStoredCookie
import com.hebeu.emptyclassroom.data.session.SessionStateHolder
import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.model.LoginOutcome
import com.hebeu.emptyclassroom.domain.model.SessionState
import com.hebeu.emptyclassroom.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * 登录/会话实现。
 *
 * 安全约束（契约 §10 / architecture §6）：
 * - 密码经 RSA 发送；主动开启自动登录时另由 CredentialStore 加密保存；
 * - Cookie 写入加密 SessionStore；普通偏好只保存学号与查询条件。
 */
class AuthRepositoryImpl(
    private val http: ZfHttpClient,
    private val loginApi: ZfLoginApi,
    private val sessionStore: SessionStore,
    private val preferencesStore: PreferencesStore,
    private val sessionHolder: SessionStateHolder,
    private val credentialStore: CredentialStore? = null,
    private val guard: SessionGuard? = null,
    private val clearPersonalData: () -> Unit = {},
) : AuthRepository {

    private val loginMutex = Mutex()
    private val accountVersion = AtomicInteger()
    @Volatile private var logoutRequested = false

    override val session: StateFlow<SessionState> = sessionHolder.state
    override suspend fun hasSavedLogin(): Boolean = withContext(Dispatchers.IO) {
        sessionStore.currentIsLoggedIn() || credentialStore?.load() != null
    }

    override suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        val hasCookies = http.restoreCookies()
        if (!hasCookies) {
            if (autoLogin()) return@withContext true
            sessionHolder.onLoggedOut()
            return@withContext false
        }
        if (!sessionStore.currentIsLoggedIn()) {
            // 只拿到匿名 Cookie（从未成功登录过），不应视为有效会话
            http.clearCookies()
            sessionHolder.onLoggedOut()
            return@withContext false
        }
        val valid: Boolean = try {
            loginApi.verifySession()
        } catch (e: AppError.Network) {
            // 离线启动：保留本地会话进入查询页，查询时会明确提示网络错误，而不是强制重新登录
            return@withContext restoreOptimistically()
        } catch (e: AppError.Timeout) {
            return@withContext restoreOptimistically()
        } catch (e: AppError.Server) {
            return@withContext restoreOptimistically()
        }
        if (valid) {
            sessionHolder.onLoggedIn(currentUsername())
            true
        } else {
            if (autoLogin()) return@withContext true
            http.clearCookies()
            sessionStore.clearSession()
            sessionHolder.onExpired(SESSION_EXPIRED_MESSAGE)
            false
        }
    }

    override suspend fun login(username: String, password: String): LoginOutcome = login(username, password, false)

    override suspend fun login(username: String, password: String, autoLogin: Boolean): LoginOutcome = withContext(Dispatchers.IO) {
        logoutRequested = false
        val version = accountVersion.incrementAndGet()
        val sameAccount = (sessionHolder.current as? SessionState.LoggedIn)?.username == username
        if (!sameAccount) guard?.invalidate()
        loginMutex.withLock {
        val previousCookies = if (sameAccount) http.cookieJar.snapshot().map { it.toStoredCookie() } else emptyList()
        try {
            loginApi.login(username, password)
            if (version != accountVersion.get() || logoutRequested) throw CancellationException("Account changed")
            if (sessionStore.currentUsername() != username) clearPersonalData()
            if (autoLogin) credentialStore?.save(SavedCredentials(username, password)) else credentialStore?.clear()
            // 成功：持久化会话标记与学号；密码由上面的独立凭据存储管理。
            sessionStore.setLoggedIn(true)
            sessionStore.saveUsername(username)
            preferencesStore.saveUsername(username)
            sessionHolder.onLoggedIn(username)
            LoginOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppError) {
            if (sameAccount && version == accountVersion.get() && !logoutRequested) {
                http.cookieJar.restore(previousCookies)
                http.persistCookies()
            }
            if (e is AppError.SessionExpired) {
                sessionHolder.onLoggedOut()
            }
            LoginOutcome.Failure(e)
        } catch (e: Exception) {
            LoginOutcome.Failure(AppError.Server(null, cause = e))
        }
        }
    }

    /** 由 SessionGuard 串行触发。无凭据或凭据失效时交回交互登录。 */
    suspend fun autoLogin(): Boolean = withContext(Dispatchers.IO) {
        loginMutex.withLock {
            if (logoutRequested) return@withLock false
            val saved = credentialStore?.load() ?: return@withLock false
            val version = accountVersion.get()
            try {
                loginApi.login(saved.username, saved.password)
                if (logoutRequested || version != accountVersion.get()) throw CancellationException("Account changed")
                sessionStore.setLoggedIn(true)
                sessionStore.saveUsername(saved.username)
                sessionHolder.onLoggedIn(saved.username)
                true
            } catch (e: AppError.InvalidCredentials) { credentialStore.clear(); false
            } catch (e: AppError.CaptchaRequired) { credentialStore.clear(); false }
        }
    }

    override suspend fun isAutoLoginEnabled(): Boolean = withContext(Dispatchers.IO) { credentialStore?.load() != null }
    override suspend fun disableAutoLogin() = withContext(Dispatchers.IO) { credentialStore?.clear(); Unit }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        logoutRequested = true
        accountVersion.incrementAndGet()
        guard?.invalidate()
        credentialStore?.clear()
        http.cancelCalls()
        sessionHolder.onLoggedOut()
        clearPersonalData()
        loginMutex.withLock {
        runCatching { loginApi.logout() }
        sessionStore.clearSession()
        clearPersonalData()
        sessionHolder.onLoggedOut()
        }
    }

    private suspend fun restoreOptimistically(): Boolean {
        val username = currentUsername()
        sessionHolder.onLoggedIn(username)
        return true
    }

    private suspend fun currentUsername(): String =
        sessionStore.currentUsername().ifBlank { preferencesStore.currentLastUsername() }

    private companion object {
        const val SESSION_EXPIRED_MESSAGE = "登录已过期，请重新登录"
    }
}
