package com.hebeu.emptyclassroom.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hebeu.emptyclassroom.domain.model.SessionState
import com.hebeu.emptyclassroom.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(private val auth: AuthRepository,
    private val privacy: PrivacyConsentRepository = MemoryPrivacyConsentRepository()) : ViewModel() {
    private val restoring = MutableStateFlow(true)
    val isRestoring = restoring.asStateFlow()
    val consentRecord = privacy.record
    private val dialog = MutableStateFlow(false)
    val showConsent = dialog.asStateFlow()
    private val error = MutableStateFlow<String?>(null)
    val consentError = error.asStateFlow()
    private val accepting = MutableStateFlow(false)
    val acceptingConsent = accepting.asStateFlow()
    private val login = Channel<Unit>(Channel.BUFFERED)
    val continueLogin = login.receiveAsFlow()
    private enum class Pending { Login, Restore }
    private var pending: Pending? = null
    private var restored = false
    private var sessionOwner: Pair<String, ViewModelStoreOwner>? = null
    init {
        viewModelScope.launch {
            if (accepted()) restoreOnce()
            else {
                val saved = runCatching { auth.hasSavedLogin() }.getOrDefault(false)
                if (saved || auth.session.value is SessionState.LoggedIn) { pending = Pending.Restore; dialog.value = true }
                restoring.value = false
            }
        }
        viewModelScope.launch { auth.session.collect { if (it !is SessionState.LoggedIn) clearSessionOwner() } }
    }
    fun accepted() = privacy.record.value?.let { it.version == PRIVACY_VERSION && it.acceptedAt > 0 } == true
    fun requestLogin() {
        if (accepted()) login.trySend(Unit)
        else { pending = Pending.Login; error.value = null; dialog.value = true }
    }
    fun declineConsent() { if (!accepting.value) { pending = null; dialog.value = false; error.value = null; restoring.value = false } }
    fun acceptConsent() {
        if (accepting.value) return
        accepting.value = true
        viewModelScope.launch {
            val action = pending
            try {
                if (action == Pending.Restore) restoring.value = true
                privacy.accept(PRIVACY_VERSION)
                dialog.value = false; pending = null
                if (action == Pending.Login) login.send(Unit) else if (action == Pending.Restore) restoreOnce()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "保存失败，请重试"; restoring.value = false }
            finally { accepting.value = false }
        }
    }
    private suspend fun restoreOnce() {
        if (restored || !accepted()) { restoring.value = false; return }
        restored = true; restoring.value = true
        try { auth.restoreSession() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
        finally { restoring.value = false }
    }
    fun sessionOwnerFor(username: String): ViewModelStoreOwner {
        sessionOwner?.takeIf { it.first == username }?.let { return it.second }
        clearSessionOwner()
        return object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }.also { sessionOwner = username to it }
    }
    private fun clearSessionOwner() { sessionOwner?.second?.viewModelStore?.clear(); sessionOwner = null }
    fun logout(onFinished: (() -> Unit)? = null) { viewModelScope.launch { runCatching { auth.logout() }; onFinished?.invoke() } }
    override fun onCleared() { clearSessionOwner(); super.onCleared() }
    companion object {
        fun factory(auth: AuthRepository, privacy: PrivacyConsentRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(auth, privacy) }
        }
    }
}
