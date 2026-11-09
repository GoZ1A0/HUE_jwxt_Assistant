package com.hebeu.emptyclassroom.data.repository

import com.hebeu.emptyclassroom.data.local.PreferencesStore
import com.hebeu.emptyclassroom.domain.model.SavedQuery
import com.hebeu.emptyclassroom.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 偏好设置实现：只存「上次查询条件」与「上次登录学号」，无任何凭据。 */
class PreferencesRepositoryImpl(
    private val store: PreferencesStore,
) : PreferencesRepository {

    override val savedQuery: Flow<SavedQuery?> = store.savedQuery

    override suspend fun saveQuery(query: SavedQuery) = withContext(Dispatchers.IO) {
        store.saveQuery(query)
    }

    override val lastUsername: Flow<String> = store.lastUsername
    override val startupSyncEnabled: Flow<Boolean> = store.startupSyncEnabled
    override suspend fun setStartupSyncEnabled(enabled: Boolean) = store.setStartupSyncEnabled(enabled)

    override suspend fun saveUsername(username: String) = withContext(Dispatchers.IO) {
        store.saveUsername(username)
    }
}
