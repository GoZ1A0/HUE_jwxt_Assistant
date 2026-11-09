package com.hebeu.emptyclassroom

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hebeu.emptyclassroom.di.AppContainer
import com.hebeu.emptyclassroom.ui.AppRoot
import com.hebeu.emptyclassroom.ui.theme.EmptyClassroomTheme

/**
 * 单 Activity 入口：
 *  - [enableEdgeToEdge] 让状态栏/导航栏透明，Scaffold 负责 insets（ui-spec §6）；
 *  - 依赖由 [AppContainer]（手写 DI，app-data 提供）创建，UI 只通过接口使用；
 *    使用进程内单例 [AppContainer.get]，保证 CookieJar/OkHttpClient 唯一（旋转/重建不丢会话）；
 *  - 页面切换、会话门控在 [AppRoot] 内完成（登录页 ↔ 查询页）。
 */
class MainActivity : ComponentActivity() {
    private var openScheduleRequest by mutableIntStateOf(0)

    private val appContainer: AppContainer by lazy { AppContainer.get(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_schedule", false)) openScheduleRequest++
        enableEdgeToEdge()
        setContent {
            EmptyClassroomTheme {
                AppRoot(
                    authRepository = appContainer.authRepository,
                    classroomRepository = appContainer.classroomRepository,
                    preferencesRepository = appContainer.preferencesRepository,
                    academicRepository = appContainer.academicRepository,
                    timetableStore = appContainer.timetableStore,
                    reminders = appContainer.courseReminders,
                    openScheduleRequest = openScheduleRequest,
                    privacyRepository = appContainer.privacyConsent,
                    startupSyncGate = appContainer.startupSyncGate,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_schedule", false)) openScheduleRequest++
    }
}
