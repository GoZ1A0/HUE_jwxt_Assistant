package com.hebeu.emptyclassroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.hebeu.emptyclassroom.domain.repository.*
import com.hebeu.emptyclassroom.ui.account.*
import com.hebeu.emptyclassroom.ui.academic.*
import com.hebeu.emptyclassroom.ui.query.*
import com.hebeu.emptyclassroom.ui.settings.*
import com.hebeu.emptyclassroom.data.local.TimetableWorkspaceStore
import com.hebeu.emptyclassroom.data.reminder.CourseReminderController

private data class MainTab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(MainTab("schedule", "课表", Icons.Outlined.CalendarMonth),
    MainTab("rooms", "空闲教室", Icons.Outlined.School), MainTab("grades", "绩点", Icons.Outlined.Analytics))

@Composable
fun MainShell(username: String, auth: AuthRepository, classrooms: ClassroomRepository,
    preferences: PreferencesRepository, academics: AcademicRepository, onLogout: () -> Unit,
    timetableStore: TimetableWorkspaceStore? = null, reminders: CourseReminderController? = null,
    openScheduleRequest: Int = 0, startupSyncGate: StartupSyncGate = StartupSyncGate(), privacyRecord: PrivacyConsent? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "schedule"
    val timetable: TimetableViewModel = viewModel(key = "timetable-$username", factory = viewModelFactory { initializer {
        TimetableViewModel(academics, timetableStore, username,
            updateReminders = { schedule, settings, times, colors, name, exams ->
                if ((auth.session.value as? com.hebeu.emptyclassroom.domain.model.SessionState.LoggedIn)?.username == username)
                    reminders?.update(schedule, settings, times, colors, name, exams)?.message.orEmpty() else ""
            }, startupSync = startupSyncGate, startupEnabled = preferences.startupSyncEnabled,
            saveStartupEnabled = preferences::setStartupSyncEnabled)
    } })
    val timetableState by timetable.state.collectAsStateWithLifecycle()
    fun go(destination: String) { nav.navigate(destination) { launchSingleTop = true } }
    val menu = AppMenuActions({ go("my") }, { go("timetable_settings") }, { go("about") })
    BackHandler(enabled = route in listOf("rooms", "grades")) {
        nav.navigate("schedule") { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    LaunchedEffect(openScheduleRequest) { if (openScheduleRequest > 0) go("schedule") }
    if (timetableState.showUpdateReview) AcademicReviewDialog(timetableState, timetable::acceptUpdates, timetable::deferUpdates)
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        if (tabs.any { it.route == route }) NavigationBar {
            tabs.forEach { tab -> NavigationBarItem(selected = route == tab.route, onClick = {
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) }) }
        }
    }) { padding ->
        NavHost(nav, startDestination = "schedule", modifier = Modifier.fillMaxSize().padding(padding)) {
            composable("rooms") {
                val query: QueryViewModel = viewModel(factory = QueryViewModel.factory(classrooms, preferences))
                val quick: QuickLookupViewModel = viewModel(factory = viewModelFactory { initializer { QuickLookupViewModel(classrooms) } })
                QueryRoute(query, onLogout, onLogout, quickLookup = quick, menuActions = menu)
            }
            composable("schedule") { TimetableRoute(timetable, menu.onMy, onLogout, menuActions = menu, handleReview = false) }
            composable("timetable_settings") {
                TimetableRoute(timetable, menu.onMy, onLogout, menuActions = menu, settingsPage = true,
                    onBack = { nav.popBackStack() }, handleReview = false)
            }
            composable("grades") {
                val vm: GradesViewModel = viewModel(factory = viewModelFactory { initializer { GradesViewModel(academics) } })
                GradesRoute(vm, menuActions = menu)
            }
            composable("my") { MyScreen(username, { go("account") }, { go("privacy") }, { nav.popBackStack() }) }
            composable("account") {
                val vm: AccountViewModel = viewModel(factory = viewModelFactory { initializer { AccountViewModel(auth) } })
                AccountRoute(vm, username, onLogout, onBack = { nav.popBackStack() })
            }
            composable("privacy") { PrivacyScreen(privacyRecord) { nav.popBackStack() } }
            composable("about") { AboutAppScreen({ nav.popBackStack() }, { go("licenses") }) }
            composable("licenses") { OpenSourceScreen { nav.popBackStack() } }
        }
    }
}
