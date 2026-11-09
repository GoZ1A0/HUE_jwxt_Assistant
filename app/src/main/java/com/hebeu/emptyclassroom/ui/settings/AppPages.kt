package com.hebeu.emptyclassroom.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.BuildConfig
import com.hebeu.emptyclassroom.domain.repository.PRIVACY_VERSION
import com.hebeu.emptyclassroom.domain.repository.PrivacyConsent
import com.hebeu.emptyclassroom.ui.common.components.AppBrandIcon
import com.hebeu.emptyclassroom.ui.academic.AcademicDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppInfo {
    const val NAME = "HUE教务助手"
    val version: String get() = BuildConfig.VERSION_NAME
    const val AUTHOR = "水果"
    const val QQ = "3483212948"
    const val EMAIL = "3483212948@qq.com"
    const val PROJECT = "https://github.com/GoZ1A0/HUE_jwxt_Assistant"
}

data class AppMenuActions(val onMy: () -> Unit, val onTimetableSettings: () -> Unit, val onAbout: () -> Unit)

@Composable
fun AppOverflowMenu(actions: AppMenuActions, tint: Color = LocalContentColor.current) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.MoreVert, "更多操作", tint = tint) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            fun go(block: () -> Unit) { expanded = false; block() }
            DropdownMenuItem(text = { Text("我的") }, leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) }, onClick = { go(actions.onMy) })
            DropdownMenuItem(text = { Text("课表设置") }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) }, onClick = { go(actions.onTimetableSettings) })
            DropdownMenuItem(text = { Text("关于本应用") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { go(actions.onAbout) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(title) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun SettingsEntry(title: String, description: String? = null, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
fun MyScreen(username: String, onAccount: () -> Unit, onPrivacy: () -> Unit, onBack: () -> Unit) {
    SettingsPage("我的", onBack) {
        AppBrandIcon(); Text(AppInfo.NAME, style = MaterialTheme.typography.headlineSmall)
        Text("当前账号：$username", style = MaterialTheme.typography.bodyLarge)
        SettingsEntry("账号与自动登录", "管理登录保持、自动登录和切换账号", onAccount)
        SettingsEntry("隐私", "隐私说明、数据来源与本地存储", onPrivacy)
    }
}

private val privacySections = listOf(
    "我们如何使用信息" to "学号、登录会话、课表、成绩及考试安排用于你使用的教务查询。应用直接连接河北工程大学教务系统（jwglxxfwpt.hebeu.edu.cn）：登录时向学校发送学号及必要认证信息，查询时携带会话信息。应用不向开发者自建服务器、第三方统计或广告平台上传这些个人教务数据，不包含广告或统计上报。点击项目或反馈链接时，将由系统打开对应的网站或应用。",
    "登录保持与密码" to "应用启动时可恢复已有有效会话。只有主动开启自动登录后，才会在本机保存供自动认证使用的密码；密码和会话 Cookie 使用 Android Keystore 支持的加密机制保存。关闭自动登录会删除保存的密码，但不会立即注销当前会话。",
    "本地数据与清理范围" to "教务课表、成绩缓存、考试与消息快照、自定义课表、背景图片和偏好保存在本机。其中课表与缓存不是全部加密文件；敏感凭据及课表文件保存在系统备份排除目录。退出或切换账号会清除保存的密码、当前会话和教务查询缓存，并停止课程提醒；上次学号、查询偏好和按账号保存的课表工作区（含导入课表、已确认安排及同步记录）保留。你可以在课表管理中删除不需要的本地课表，也可通过系统应用设置清除全部应用数据。导出的文件由你自行保管。",
    "权限与课程提醒" to "通知权限用于课程和考试提醒；精确提醒权限用于按设定时间安排本机闹钟。可随时在课表设置中关闭提醒，也可在系统设置中管理权限。导入课表和背景图片通过系统文件选择器由你主动选择。开启课程实况时，必要课程摘要会提供给系统通知或受支持的本机系统组件；显示效果由手机系统决定。应用不申请通讯录或定位权限。",
    "数据来源与查询说明" to "课程、成绩、绩点、调课消息、考试安排及空闲教室数据来自学校教务系统，展示上次同步时间。官方 GPA 按学校返回展示；学校尚未公布的信息会标注未公布。空闲教室仅表示所选教学时段没有相应排课，不代表现场一定无人，也不代替学校场地管理规定；课间速查按下一教学节次查询。",
    "你可以控制的事项" to "你可以关闭启动自动更新课表，手动检查教务更新；调课和考试变更仍需确认后才应用，关联副本保留手动修改的冲突处理与撤销能力。你也可以关闭自动登录、退出或切换账号、关闭提醒、删除本地课表或清除系统应用数据。",
    "应用与联系方式" to "HUE教务助手由水果独立开发，不是学校官方应用。隐私或使用问题可联系 QQ：3483212948，邮箱：3483212948@qq.com。"
)

@Composable
fun PrivacyScreen(record: PrivacyConsent?, onBack: () -> Unit) {
    SettingsPage("隐私", onBack) {
        Text("隐私与数据使用说明", style = MaterialTheme.typography.headlineSmall)
        Text("政策版本 $PRIVACY_VERSION · 更新于 2026-11-09", style = MaterialTheme.typography.labelMedium)
        Text(record?.takeIf { it.version == PRIVACY_VERSION && it.acceptedAt > 0 }?.let {
            "已同意 · " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(it.acceptedAt))
        } ?: "尚未同意当前政策", style = MaterialTheme.typography.bodySmall)
        privacySections.forEach { (title, text) ->
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PrivacyConsentDialog(busy: Boolean, error: String?, onAgree: () -> Unit, onDecline: () -> Unit, onPolicy: () -> Unit) {
    AcademicDialog("隐私知情同意书", onDismiss = { if (!busy) onDecline() }, actions = {
        TextButton(onClick = onDecline, enabled = !busy) { Text("暂不同意") }
        TextButton(onClick = onAgree, enabled = !busy) { Text("同意并继续") }
    }) {
            Text("使用 HUE教务助手前，请了解：")
            Text("• 登录和查询直接访问学校教务系统，会向学校发送必要的账号认证与会话信息。")
            Text("• 应用不向开发者自建服务器、统计或广告平台上传你的个人教务数据。密码仅在开启自动登录后于本机加密保存；其他缓存和自定义课表在本机保存。")
            Text("• 你可以关闭自动登录、启动同步和提醒，并通过课表管理或系统设置清理本地数据。应用由水果独立开发，非学校官方应用。")
            TextButton(onClick = onPolicy, enabled = !busy) { Text("查看完整隐私说明") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun AboutAppScreen(onBack: () -> Unit, onOpenSource: () -> Unit) {
    val context = LocalContext.current
    fun copy(value: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("联系方式", value))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
    fun open(value: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }.onFailure { copy(value) } }
    SettingsPage("关于本应用", onBack) {
        AppBrandIcon(); Text(AppInfo.NAME, style = MaterialTheme.typography.headlineSmall)
        Text("版本 ${AppInfo.version}", style = MaterialTheme.typography.titleMedium)
        Text("面向河北工程大学学生的教务查询助手：查看课表、寻找自习教室、查询成绩绩点，确认调课更新并管理考试日程。")
        Text("作者：${AppInfo.AUTHOR}", style = MaterialTheme.typography.titleMedium)
        ContactRow("QQ", AppInfo.QQ, { copy(AppInfo.QQ) })
        ContactRow("邮箱", AppInfo.EMAIL, { copy(AppInfo.EMAIL) }, { open("mailto:${AppInfo.EMAIL}") })
        ContactRow("项目与问题反馈", AppInfo.PROJECT, { copy(AppInfo.PROJECT) }, { open(AppInfo.PROJECT) })
        Text("1.1.0 更新", style = MaterialTheme.typography.titleMedium)
        Text("应用更名为 HUE教务助手；课表支持左右滑动翻周；统一菜单和课表设置；新增启动同步开关、隐私知情同意与关于页面。")
        SettingsEntry("开源组件", "查看主要依赖项目及其许可证", onOpenSource)
    }
}

@Composable
private fun ContactRow(label: String, value: String, onCopy: () -> Unit, onOpen: (() -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, Modifier.fillMaxWidth().clickable(onClick = onCopy).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Row { TextButton(onClick = onCopy) { Text("复制") }; onOpen?.let { TextButton(onClick = it) { Text("打开") } } }
    } }
}

@Composable
fun OpenSourceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage("开源组件", onBack) {
        Text("以下为主要运行组件，其许可证与声明以各项目公布的信息为准。")
        listOf("AndroidX / Jetpack Compose" to "https://github.com/androidx/androidx", "Kotlin / kotlinx.coroutines" to "https://github.com/Kotlin/kotlinx.coroutines",
            "kotlinx.serialization" to "https://github.com/Kotlin/kotlinx.serialization", "OkHttp" to "https://github.com/square/okhttp", "jsoup" to "https://github.com/jhy/jsoup").forEach { (name, url) ->
            SettingsEntry(name, url) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
        }
    }
}
