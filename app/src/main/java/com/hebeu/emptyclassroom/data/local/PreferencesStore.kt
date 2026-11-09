package com.hebeu.emptyclassroom.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hebeu.emptyclassroom.domain.model.SavedQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 偏好存储（DataStore 文件 `preferences`）：上次查询条件 + 上次登录学号。
 *
 * 隐私：这里不保存密码、Cookie 或任何凭据；Cookie 在 [SessionStore]。
 */
class PreferencesStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.preferencesDataStore)

    /** 上次查询条件；无记录或数据损坏时为 null。 */
    val savedQuery: Flow<SavedQuery?> = dataStore.data
        .map { prefs ->
            prefs[KEY_LAST_QUERY]?.let { raw ->
                runCatching { json.decodeFromString<SavedQuery>(raw) }.getOrNull()
            }
        }
        .distinctUntilChanged()

    /** 上次登录学号（登录页预填），默认为空字符串。 */
    val lastUsername: Flow<String> = dataStore.data
        .map { it[KEY_LAST_USERNAME].orEmpty() }
        .distinctUntilChanged()

    suspend fun saveQuery(query: SavedQuery) {
        dataStore.edit { it[KEY_LAST_QUERY] = json.encodeToString(query) }
    }

    suspend fun saveUsername(username: String) {
        dataStore.edit { it[KEY_LAST_USERNAME] = username }
    }

    suspend fun currentLastUsername(): String = lastUsername.first()
    val startupSyncEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_STARTUP_SYNC] ?: true }.distinctUntilChanged()
    suspend fun setStartupSyncEnabled(enabled: Boolean) { dataStore.edit { it[KEY_STARTUP_SYNC] = enabled } }

    private companion object {
        val KEY_LAST_QUERY = stringPreferencesKey("last_query")
        val KEY_LAST_USERNAME = stringPreferencesKey("last_username")
        val KEY_STARTUP_SYNC = booleanPreferencesKey("startup_sync")
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")
