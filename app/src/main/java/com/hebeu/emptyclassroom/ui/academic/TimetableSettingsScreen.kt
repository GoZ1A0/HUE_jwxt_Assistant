package com.hebeu.emptyclassroom.ui.academic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.ui.settings.SettingsEntry
import com.hebeu.emptyclassroom.ui.settings.SettingsPage

@Composable
fun TimetableSettingsScreen(state: TimetableState, onStartup: (Boolean) -> Unit, onManage: () -> Unit,
    onCalendar: () -> Unit, onStyle: () -> Unit, onReminders: () -> Unit, onToggleList: () -> Unit,
    onSync: () -> Unit, onHistory: () -> Unit, onExams: () -> Unit, onImport: () -> Unit,
    onExport: () -> Unit, onBack: () -> Unit, onWidgets: () -> Unit = {}) {
    SettingsPage("课表设置", onBack) {
        Text(state.name, style = MaterialTheme.typography.titleLarge)
        ElevatedCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("启动应用自动更新课表", style = MaterialTheme.typography.titleMedium)
                    Text("每次冷启动检查一次课表、消息和考试；变更仍需确认。", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Switch(state.startupSyncEnabled, onCheckedChange = onStartup)
            }
        }
        Text("课表管理", style = MaterialTheme.typography.titleSmall)
        SettingsEntry("管理课表", "选择、新建、复制或删除课表", onManage)
        SettingsEntry("学期与作息", "开学日期、教学周数和节次时间", onCalendar)
        Text("外观与显示", style = MaterialTheme.typography.titleSmall)
        SettingsEntry("自定义课表样式", "背景、配色、字体和课程卡片", onStyle)
        SettingsEntry(if (state.listMode) "切换周课表" else "切换列表", "当前为${if (state.listMode) "列表" else "周视图"}", onToggleList)
        Text("考试与提醒", style = MaterialTheme.typography.titleSmall)
        SettingsEntry("提醒与课程实况", "课程、考试提醒与系统展示", onReminders)
        SettingsEntry("考试日程", "按实际日期查看考试与时间冲突", onExams)
        SettingsEntry("桌面小组件", "添加今日课表到手机桌面", onWidgets)
        Text("教务同步", style = MaterialTheme.typography.titleSmall)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsEntry("检查教务更新", "拉取课表、调课消息、考试和课程成绩", onSync)
        SettingsEntry("教务消息与同步记录", if (state.sync?.pending == true) "有待确认的更新" else "查看通知、历史记录与撤销", onHistory)
        Text("备份", style = MaterialTheme.typography.titleSmall)
        SettingsEntry("导入课表", "WakeUp 备份或本应用 JSON", onImport)
        if (state.schedule != null) SettingsEntry("导出当前课表", "保存本应用 JSON 备份", onExport)
    }
}
