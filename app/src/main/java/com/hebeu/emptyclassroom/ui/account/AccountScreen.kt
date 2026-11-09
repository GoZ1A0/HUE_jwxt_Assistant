package com.hebeu.emptyclassroom.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebeu.emptyclassroom.domain.model.LoginOutcome
import com.hebeu.emptyclassroom.domain.repository.AuthRepository
import com.hebeu.emptyclassroom.ui.common.AppErrorText
import com.hebeu.emptyclassroom.ui.common.components.AppBrandIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountState(val enabled: Boolean = false, val busy: Boolean = false, val error: String? = null)
class AccountViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()
    init { viewModelScope.launch { _state.update { it.copy(enabled = auth.isAutoLoginEnabled()) } } }
    fun disable() { viewModelScope.launch { auth.disableAutoLogin(); _state.value = AccountState() } }
    fun enable(username: String, password: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = auth.login(username, password, true)) {
                    LoginOutcome.Success -> _state.value = AccountState(enabled = true)
                    is LoginOutcome.Failure -> _state.value = AccountState(error = AppErrorText.loginMessage(result.error))
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _state.value = AccountState(error = AppErrorText.loginMessage(e)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountRoute(viewModel: AccountViewModel, username: String, onLogout: () -> Unit, onBack: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    LaunchedEffect(state.enabled) { if (state.enabled) { password = ""; showPassword = false } }
    if (showPassword) AlertDialog(onDismissRequest = { if (!state.busy) { showPassword = false; password = "" } },
        title = { Text("开启自动登录") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("输入教务密码，验证成功后在本机加密保存。")
                OutlinedTextField(password, onValueChange = { password = it }, label = { Text("教务密码") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = { viewModel.enable(username, password) }, enabled = password.isNotBlank() && !state.busy) { Text("开启") } },
        dismissButton = { TextButton(onClick = { showPassword = false; password = "" }, enabled = !state.busy) { Text("取消") } })
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("账号与自动登录") }, navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AppBrandIcon()
            Text("HUE教务助手", style = MaterialTheme.typography.headlineSmall)
            Text(username, style = MaterialTheme.typography.titleMedium)
            ElevatedCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("自动登录", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = state.enabled, onCheckedChange = { if (it) showPassword = true else viewModel.disable() }, enabled = !state.busy)
                    }
                    Text("优先恢复已有会话；过期后自动重新登录。关闭开关会删除保存的密码。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("版本 ${com.hebeu.emptyclassroom.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录 / 切换账号") }
        }
    }
}
