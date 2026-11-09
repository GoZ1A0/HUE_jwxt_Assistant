package com.hebeu.emptyclassroom.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 持久化的单条 Cookie（用于退出 App 后恢复会话）。
 *
 * ⚠️ 只存本机 DataStore，禁止写入日志；[value] 属于敏感数据。
 */
@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAt: Long = Long.MAX_VALUE,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val hostOnly: Boolean = false,
    val persistent: Boolean = true,
)

/**
 * 会话存储：Cookie 列表 + 已登录标记 + 上次登录学号。
 *
 * 使用独立的 DataStore 文件（`session`），与偏好设置（`preferences`）分开，
 * 便于登出时只清会话。密码从不写入任何存储。
 */
class SessionStore(private val dataStore: DataStore<Preferences>, private val cipher: SecretCipher? = null) {

    constructor(context: Context) : this(context.sessionDataStore, KeystoreCipher())

    /** 上次登录学号（仅用于显示/预填，不是凭据）。 */
    val username: Flow<String> = dataStore.data
        .map { it[KEY_USERNAME].orEmpty() }
        .distinctUntilChanged()

    /** 是否曾成功登录过（用于区分「从未登录」与「会话过期」）。 */
    val isLoggedIn: Flow<Boolean> = dataStore.data
        .map { it[KEY_LOGGED_IN] ?: false }
        .distinctUntilChanged()

    suspend fun currentIsLoggedIn(): Boolean = isLoggedIn.first()

    suspend fun currentUsername(): String = username.first()

    /** 读取持久化 Cookie；损坏的 JSON 视为空（不会导致崩溃）。 */
    suspend fun loadCookies(): List<StoredCookie> {
        val prefs = dataStore.data.first()
        val encrypted = prefs[KEY_ENCRYPTED_COOKIES]
        if (encrypted != null) return runCatching {
            json.decodeFromString<List<StoredCookie>>(requireNotNull(cipher).decrypt(encrypted))
        }.getOrElse { clearSession(); emptyList() }
        val raw = prefs[KEY_COOKIES] ?: return emptyList()
        val cookies = runCatching { json.decodeFromString<List<StoredCookie>>(raw) }.getOrDefault(emptyList())
        if (cipher != null) saveCookies(cookies) // 从旧版明文存储迁移。
        return cookies
    }

    suspend fun saveCookies(cookies: List<StoredCookie>) {
        val value = cookies.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
        val encrypted = if (cipher != null && value != null) cipher.encrypt(value) else null
        dataStore.edit { prefs ->
            prefs.remove(KEY_COOKIES)
            prefs.remove(KEY_ENCRYPTED_COOKIES)
            if (encrypted != null) prefs[KEY_ENCRYPTED_COOKIES] = encrypted
            else if (value != null) prefs[KEY_COOKIES] = value
        }
    }

    suspend fun saveUsername(username: String) {
        dataStore.edit { it[KEY_USERNAME] = username }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        dataStore.edit { it[KEY_LOGGED_IN] = loggedIn }
    }

    /** 登出：清 Cookie 与登录标记，保留学号用于登录页预填。 */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_COOKIES)
            prefs.remove(KEY_ENCRYPTED_COOKIES)
            prefs[KEY_LOGGED_IN] = false
        }
    }

    private companion object {
        val KEY_COOKIES = stringPreferencesKey("session_cookies")
        val KEY_ENCRYPTED_COOKIES = stringPreferencesKey("session_cookies_encrypted_v1")
        val KEY_USERNAME = stringPreferencesKey("session_username")
        val KEY_LOGGED_IN = booleanPreferencesKey("session_logged_in")
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
