package com.hebeu.emptyclassroom.di

import android.content.Context
import com.hebeu.emptyclassroom.data.local.PreferencesStore
import com.hebeu.emptyclassroom.data.local.SessionStore
import com.hebeu.emptyclassroom.data.remote.CdjyApi
import com.hebeu.emptyclassroom.data.remote.PersistentCookieJar
import com.hebeu.emptyclassroom.data.remote.ZfHttpClient
import com.hebeu.emptyclassroom.data.remote.ZfLoginApi
import com.hebeu.emptyclassroom.data.repository.AuthRepositoryImpl
import com.hebeu.emptyclassroom.data.repository.ClassroomRepositoryImpl
import com.hebeu.emptyclassroom.data.repository.PreferencesRepositoryImpl
import com.hebeu.emptyclassroom.data.session.SessionStateHolder
import com.hebeu.emptyclassroom.domain.repository.AuthRepository
import com.hebeu.emptyclassroom.domain.repository.ClassroomRepository
import com.hebeu.emptyclassroom.domain.repository.PreferencesRepository
import com.hebeu.emptyclassroom.data.local.KeystoreCipher
import com.hebeu.emptyclassroom.data.local.EncryptedCredentialStore
import com.hebeu.emptyclassroom.data.local.FileAcademicCache
import com.hebeu.emptyclassroom.data.remote.AcademicApi
import com.hebeu.emptyclassroom.data.repository.AcademicRepositoryImpl
import com.hebeu.emptyclassroom.data.session.SessionGuard
import com.hebeu.emptyclassroom.domain.repository.AcademicRepository

/**
 * 手写依赖容器（无 DI 框架）。
 *
 * 两种用法都支持：
 * ```kotlin
 * val container = AppContainer(context)          // 直接构造
 * val container = AppContainer.get(context)      // 进程内单例（推荐）
 * container.authRepository / classroomRepository / preferencesRepository
 * ```
 *
 * 说明：DataStore 实例由 `preferencesDataStore` 委托缓存为进程级单例，
 * 因此重复构造 AppContainer 不会触发 DataStore 多实例异常；
 * 但 **CookieJar/OkHttpClient 是每实例独立的**，为共享登录会话，生产代码仍建议用 [get]。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val sessionStore = SessionStore(appContext)
    private val preferencesStore = PreferencesStore(appContext)
    private val cookieJar = PersistentCookieJar()
    private val httpClient = ZfHttpClient(
        baseUrl = ZfHttpClient.BASE_URL,
        cookieJar = cookieJar,
        sessionStore = sessionStore,
    )
    private val loginApi = ZfLoginApi(httpClient)
    private val cdjyApi = CdjyApi(httpClient)
    private val sessionHolder = SessionStateHolder()
    private val sessionGuard = SessionGuard(sessionHolder)
    private val academicCache = FileAcademicCache(appContext)
    val timetableStore = com.hebeu.emptyclassroom.data.local.FileTimetableWorkspaceStore(appContext)
    val courseReminders = com.hebeu.emptyclassroom.data.reminder.CourseReminderController(appContext)
    val privacyConsent = com.hebeu.emptyclassroom.data.local.PrivacyConsentStore(appContext)
    val startupSyncGate = com.hebeu.emptyclassroom.domain.repository.StartupSyncGate()

    private val authImpl = AuthRepositoryImpl(
        http = httpClient,
        loginApi = loginApi,
        sessionStore = sessionStore,
        preferencesStore = preferencesStore,
        sessionHolder = sessionHolder,
        credentialStore = EncryptedCredentialStore(appContext, KeystoreCipher()),
        guard = sessionGuard,
        clearPersonalData = { academicCache.clear(); courseReminders.cancel(true) },
    )
    val authRepository: AuthRepository = authImpl
    init { sessionGuard.recover = authImpl::autoLogin }

    val classroomRepository: ClassroomRepository = ClassroomRepositoryImpl(
        api = cdjyApi,
        sessionHolder = sessionHolder,
        guard = sessionGuard,
    )

    val academicRepository: AcademicRepository = AcademicRepositoryImpl(AcademicApi(httpClient),
        cdjyApi, classroomRepository, sessionHolder, sessionGuard, academicCache)

    val preferencesRepository: PreferencesRepository = PreferencesRepositoryImpl(preferencesStore)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        /** 进程内单例（推荐入口，保证 CookieJar/OkHttpClient 唯一）。 */
        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
