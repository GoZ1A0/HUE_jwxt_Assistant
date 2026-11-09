package com.hebeu.emptyclassroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hebeu.emptyclassroom.domain.model.SessionState
import com.hebeu.emptyclassroom.domain.repository.*
import com.hebeu.emptyclassroom.ui.login.*
import com.hebeu.emptyclassroom.ui.settings.*
import com.hebeu.emptyclassroom.ui.common.components.AppBrandIcon
import com.hebeu.emptyclassroom.ui.motion.rememberMotionSpec

private enum class AppScreen { Restoring, Login, Main }

@Composable
fun AppRoot(authRepository: AuthRepository, classroomRepository: ClassroomRepository,
    preferencesRepository: PreferencesRepository, academicRepository: AcademicRepository,
    modifier: Modifier = Modifier,
    timetableStore: com.hebeu.emptyclassroom.data.local.TimetableWorkspaceStore? = null,
    reminders: com.hebeu.emptyclassroom.data.reminder.CourseReminderController? = null,
    openScheduleRequest: Int = 0,
    privacyRepository: PrivacyConsentRepository? = null,
    startupSyncGate: StartupSyncGate? = null) {
    val privacy = privacyRepository ?: remember { MemoryPrivacyConsentRepository() }
    val startup = startupSyncGate ?: remember { StartupSyncGate() }
    val app: AppViewModel = viewModel(factory = AppViewModel.factory(authRepository, privacy))
    val login: LoginViewModel = viewModel(factory = LoginViewModel.factory(authRepository, preferencesRepository))
    val restoring by app.isRestoring.collectAsStateWithLifecycle()
    val record by app.consentRecord.collectAsStateWithLifecycle()
    val session by authRepository.session.collectAsStateWithLifecycle()
    val consent by app.showConsent.collectAsStateWithLifecycle()
    val busy by app.acceptingConsent.collectAsStateWithLifecycle()
    val consentError by app.consentError.collectAsStateWithLifecycle()
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    val motion = rememberMotionSpec()
    LaunchedEffect(app, login) { app.continueLogin.collect { login.submit() } }
    BackHandler(enabled = info != null) { info = null }
    if (info != null) {
        when (info) {
            "privacy" -> PrivacyScreen(record) { info = null }
            "licenses" -> OpenSourceScreen { info = "about" }
            else -> AboutAppScreen(onBack = { info = null }, onOpenSource = { info = "licenses" })
        }
    } else {
        val accepted = record?.let { it.version == PRIVACY_VERSION && it.acceptedAt > 0 } == true
        val screen = when { restoring -> AppScreen.Restoring; accepted && session is SessionState.LoggedIn -> AppScreen.Main; else -> AppScreen.Login }
        AnimatedContent(screen, modifier.fillMaxSize(), transitionSpec = {
            (fadeIn(motion.tween(motion.standardMs)) + slideInVertically(motion.tween(motion.standardMs)) { it / 24 }) togetherWith
                (fadeOut(motion.tween(motion.shortMs)) + slideOutVertically(motion.tween(motion.shortMs)) { -it / 24 })
        }, label = "appRoot") { target ->
            when (target) {
                AppScreen.Restoring -> SplashContent()
                AppScreen.Login -> LoginRoute(login, if (session is SessionState.Expired) "登录已过期，请重新登录" else null,
                    onSubmit = {
                        val state = login.uiState.value
                        if (state.username.isBlank() || state.password.isEmpty()) login.submit() else app.requestLogin()
                    }, onPrivacy = { info = "privacy" }, onAbout = { info = "about" })
                AppScreen.Main -> {
                    val username = (session as? SessionState.LoggedIn)?.username
                    if (username != null) key(username) {
                        val owner = remember(username, app) { app.sessionOwnerFor(username) }
                        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                            MainShell(username, authRepository, classroomRepository, preferencesRepository, academicRepository,
                                onLogout = { app.logout() }, timetableStore = timetableStore, reminders = reminders,
                                openScheduleRequest = openScheduleRequest, startupSyncGate = startup, privacyRecord = record)
                        }
                    }
                }
            }
        }
        if (consent) PrivacyConsentDialog(busy, consentError, app::acceptConsent, app::declineConsent) { info = "privacy" }
    }
}

@Composable
private fun SplashContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppBrandIcon(); Text("河北工程大学", style = MaterialTheme.typography.headlineSmall)
            Text(AppInfo.NAME, style = MaterialTheme.typography.titleMedium)
            CircularProgressIndicator(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun LoginRoute(viewModel: LoginViewModel, notice: String?, onSubmit: () -> Unit, onPrivacy: () -> Unit, onAbout: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(notice) { if (notice != null) viewModel.resetForRelogin() }
    LoginScreen(state, viewModel::onUsernameChange, viewModel::onPasswordChange, viewModel::togglePasswordVisibility,
        onSubmit, onAbout, noticeText = notice, onAutoLoginChange = viewModel::setAutoLogin, onOpenPrivacy = onPrivacy)
}
