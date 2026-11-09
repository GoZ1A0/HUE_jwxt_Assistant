package com.hebeu.emptyclassroom.domain.repository

import com.hebeu.emptyclassroom.domain.model.SavedQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 偏好设置（DataStore）。只保存「上次查询条件」与「上次登录学号」，绝不保存密码。 */
interface PreferencesRepository {

    val savedQuery: Flow<SavedQuery?>

    suspend fun saveQuery(query: SavedQuery)

    val lastUsername: Flow<String>

    suspend fun saveUsername(username: String)
    val startupSyncEnabled: Flow<Boolean> get() = flowOf(true)
    suspend fun setStartupSyncEnabled(enabled: Boolean) {}
}
