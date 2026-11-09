package com.hebeu.emptyclassroom.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hebeu.emptyclassroom.domain.model.LoginOutcome
import com.hebeu.emptyclassroom.domain.repository.AuthRepository
import com.hebeu.emptyclassroom.domain.repository.PreferencesRepository
import com.hebeu.emptyclassroom.ui.common.AppErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录页 ViewModel。
 *
 * - 记住上次学号（[PreferencesRepository.lastUsername]），**不记忆密码**；
 * - 提交时取消旧请求（防重复点击），失败展示真实错误映射文案；
 * - 成功后保存学号并清空内存密码；页面切换由 AppRoot 观察 [AuthRepository.session] 驱动。
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(capacity = Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    private var loginJob: Job? = null

    init {
        // 学号记忆：只在输入框为空时回填，不覆盖用户正在编辑的内容。
        preferencesRepository.lastUsername
            .onEach { saved ->
                if (saved.isNotBlank() && _uiState.value.username.isBlank()) {
                    _uiState.update { it.copy(username = saved) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null, errorText = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorText = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun setAutoLogin(enabled: Boolean) { _uiState.update { it.copy(autoLogin = enabled) } }

    fun dismissError() {
        _uiState.update { it.copy(errorText = null) }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return
        val username = current.username.trim()
        val password = current.password
        val usernameError = if (username.isEmpty()) "请输入学号" else null
        val passwordError = if (password.isEmpty()) "请输入密码" else null
        if (usernameError != null || passwordError != null) {
            _uiState.update {
                it.copy(usernameError = usernameError, passwordError = passwordError, errorText = null)
            }
            return
        }

        loginJob?.cancel()
        _uiState.update {
            it.copy(
                isSubmitting = true,
                errorText = null,
                usernameError = null,
                passwordError = null,
            )
        }
        loginJob = viewModelScope.launch {
            try {
                when (val outcome = authRepository.login(username, password, current.autoLogin)) {
                    is LoginOutcome.Success -> {
                        runCatching { preferencesRepository.saveUsername(username) }
                        _uiState.update {
                            it.copy(isSubmitting = false, password = "", errorText = null)
                        }
                        _events.send(LoginEvent.LoggedIn(username))
                    }
                    is LoginOutcome.Failure -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorText = AppErrorText.loginMessage(outcome.error),
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isSubmitting = false, errorText = AppErrorText.loginMessage(t))
                }
            }
        }
    }

    /** 会话失效后回到登录页时调用：保留学号、清空密码与临时错误。 */
    fun resetForRelogin() {
        loginJob?.cancel()
        _uiState.update {
            it.copy(
                isSubmitting = false,
                password = "",
                isPasswordVisible = false,
                errorText = null,
                usernameError = null,
                passwordError = null,
            )
        }
    }

    override fun onCleared() {
        loginJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            preferencesRepository: PreferencesRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(authRepository, preferencesRepository) }
        }
    }
}
