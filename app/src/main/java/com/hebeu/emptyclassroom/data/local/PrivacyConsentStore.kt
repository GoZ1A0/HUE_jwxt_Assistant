package com.hebeu.emptyclassroom.data.local

import android.content.Context
import com.hebeu.emptyclassroom.domain.repository.PrivacyConsent
import com.hebeu.emptyclassroom.domain.repository.PrivacyConsentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class PrivacyConsentStore(context: Context) : PrivacyConsentRepository {
    private val preferences = context.applicationContext.getSharedPreferences("privacy_consent", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getString("version", null)?.let {
        PrivacyConsent(it, preferences.getLong("accepted_at", 0))
    })
    override val record: StateFlow<PrivacyConsent?> = state
    override suspend fun accept(version: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        check(preferences.edit().putString("version", version).putLong("accepted_at", now).commit()) { "同意记录保存失败，请重试" }
        state.value = PrivacyConsent(version, now)
    }
}
