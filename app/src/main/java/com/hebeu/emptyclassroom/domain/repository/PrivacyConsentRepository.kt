package com.hebeu.emptyclassroom.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PrivacyConsent(val version: String, val acceptedAt: Long)
interface PrivacyConsentRepository {
    val record: StateFlow<PrivacyConsent?>
    suspend fun accept(version: String)
}

class MemoryPrivacyConsentRepository(accepted: Boolean = false) : PrivacyConsentRepository {
    private val state = MutableStateFlow(if (accepted) PrivacyConsent(PRIVACY_VERSION, 1L) else null)
    override val record: StateFlow<PrivacyConsent?> = state
    override suspend fun accept(version: String) { state.value = PrivacyConsent(version, System.currentTimeMillis()) }
}

const val PRIVACY_VERSION = "2026-11-09.1"

/** 进程内共享，配置变化和页面重建不会再次认领启动同步。 */
class StartupSyncGate {
    private val claimed = mutableSetOf<String>()
    @Synchronized fun claim(account: String): Boolean = claimed.add(account)
}
