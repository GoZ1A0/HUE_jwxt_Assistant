package com.hebeu.emptyclassroom.data.session

import com.hebeu.emptyclassroom.domain.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内会话状态中枢：AuthRepository 与 ClassroomRepository 共享，
 * 保证任意数据请求发现会话失效时，UI 观察到的 `session` 同步变为 Expired。
 */
class SessionStateHolder {

    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val current: SessionState get() = _state.value

    fun onLoggedIn(username: String) {
        _state.value = SessionState.LoggedIn(username)
    }

    fun onLoggedOut() {
        _state.value = SessionState.LoggedOut
    }

    fun onExpired(reason: String? = null) {
        _state.value = SessionState.Expired(reason)
    }

    val isExpired: Boolean get() = _state.value is SessionState.Expired
}
