package com.hebeu.emptyclassroom.ui.academic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hebeu.emptyclassroom.domain.model.*
import com.hebeu.emptyclassroom.data.reminder.CourseReminderPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun AcademicDialog(title: String, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val height = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 20.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = height),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
            }
        }
    }
}

private fun placement(rows: List<Lesson>): String = if (rows.isEmpty()) "无此安排" else rows.joinToString("\n") {
    "${it.weekText} · ${it.weekday?.let { day -> "周${"一二三四五六日"[day - 1]}" } ?: "星期待定"} · ${it.periodText}\n${it.location ?: "地点待定"} · ${it.teacher.orEmpty()}"
}

@Composable
fun AcademicReviewDialog(state: TimetableState, onConfirm: (Set<String>) -> Unit, onLater: () -> Unit) {
    val sync = state.sync ?: return
    var selected by remember(sync.batchKey) { mutableStateOf(emptySet<String>()) }
    AcademicDialog("教务安排有更新", onLater, actions = {
        TextButton(onClick = onLater) { Text("稍后处理") }
        FilledTonalButton(onClick = { onConfirm(selected) }) { Text("确认更新") }
    }) {
        Text("确认后更新教务课表、关联副本和提醒。独立导入的课表保留原样。", style = MaterialTheme.typography.bodySmall)
        if (sync.courseChanges.isEmpty() && !AcademicChanges.calendarChanged(sync.accepted, sync.latest))
            Text("课程时间与地点没有变化。", style = MaterialTheme.typography.bodyMedium)
        sync.courseChanges.forEach { change -> OutlinedCard {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(change.name, style = MaterialTheme.typography.titleMedium)
                Text("原安排\n${placement(change.before)}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("新安排\n${placement(change.after)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        } }
        if (AcademicChanges.calendarChanged(sync.accepted, sync.latest)) Text("学期校历或节次作息有变化，将同时更新。")
        if (sync.examChanged) {
            Text("考试安排：${sync.exams.size} 场 → ${sync.latestExams.orEmpty().size} 场", style = MaterialTheme.typography.titleSmall)
            val before = sync.exams.map { it.signature }.toSet(); val after = sync.latestExams.orEmpty().map { it.signature }.toSet()
            sync.exams.filter { it.signature !in after }.forEach { Text("原：${it.name} · ${it.timeText} · ${it.location ?: "地点待定"}", style = MaterialTheme.typography.bodySmall) }
            sync.latestExams.orEmpty().filter { it.signature !in before }.forEach { Text("新：${it.name} · ${it.timeText} · ${it.location ?: "地点待定"}", style = MaterialTheme.typography.bodySmall) }
        }
        val remote = sync.latest
        if (remote != null) state.workspace.profiles.filter { it.schoolBase?.term?.key == remote.term.key }.forEach { profile ->
            val merge = AcademicChanges.merge(requireNotNull(profile.schoolBase), profile.schedule, remote)
            merge.conflicts.forEach { key ->
                val value = "${profile.id}|$key"
                Column {
                    Text("“${profile.name}”有手动修改与教务更新重叠", style = MaterialTheme.typography.titleSmall)
                    val mine = AcademicChanges.diff(requireNotNull(profile.schoolBase), profile.schedule).firstOrNull { it.key == key }
                    mine?.let { Text("我的安排\n${placement(it.after)}", style = MaterialTheme.typography.bodySmall) }
                    Text("默认保留你的修改；勾选后采用该课程的教务安排。", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().clickable { selected = if (value in selected) selected - value else selected + value }) {
                        Checkbox(value in selected, onCheckedChange = { selected = if (it) selected + value else selected - value })
                        Text("采用教务安排：${sync.courseChanges.firstOrNull { it.key == key }?.name ?: key.removePrefix("name:").removePrefix("code:")}", Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        if (sync.unread.isNotEmpty()) {
            Text("学校原始通知", style = MaterialTheme.typography.titleMedium)
            sync.unread.forEach { notice -> OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(notice.title, style = MaterialTheme.typography.titleSmall)
                    if (notice.body != notice.title) Text(notice.body, style = MaterialTheme.typography.bodySmall)
                    Text(notice.createdAt, style = MaterialTheme.typography.labelSmall)
                }
            } }
        }
    }
}

@Composable
fun AcademicHistoryDialog(state: TimetableState, onReview: () -> Unit, onUndo: (Long) -> Unit, onDismiss: () -> Unit) {
    val sync = state.sync
    AcademicDialog("教务消息与同步记录", onDismiss, actions = { TextButton(onClick = onDismiss) { Text("关闭") } }) {
        Text(sync?.checkedAt?.takeIf { it > 0 }?.let { "上次检查：${stamp(it)}" } ?: "尚未检查教务更新")
        if (sync?.pending == true) FilledTonalButton(onClick = { onDismiss(); onReview() }) { Text("查看待确认更新") }
        val last = sync?.history?.lastOrNull { it.undoable }?.at
        if (sync?.history.isNullOrEmpty()) Text("确认更新后，会在这里保留最近 5 次记录。")
        sync?.history?.asReversed()?.forEach { revision ->
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(revision.label, style = MaterialTheme.typography.titleSmall); Text(stamp(revision.at), style = MaterialTheme.typography.labelSmall)
                if (revision.undoable && revision.at == last) TextButton(onClick = { onUndo(revision.at) }) { Text("恢复此次更新前的课表与考试") }
            } }
        }
        sync?.notices?.forEach { notice ->
            Text(notice.title, style = MaterialTheme.typography.titleSmall)
            if (notice.body != notice.title) Text(notice.body, style = MaterialTheme.typography.bodySmall)
            Text("${notice.createdAt} · ${if (notice.key in sync.handledNotices) "已确认" else "待确认"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
private fun stamp(value: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(academicZone).format(Instant.ofEpochMilli(value))

@Composable
fun AcademicExamsDialog(state: TimetableState, onDate: (String) -> Unit, onDismiss: () -> Unit, onReview: () -> Unit = {}) {
    val courses = remember(state.schedule, state.lessonTimes) { CourseReminderPlanner.plan(state.schedule, ReminderSettings(), state.lessonTimes).courses }
    AcademicDialog("考试日程", onDismiss, actions = { TextButton(onClick = onDismiss) { Text("关闭") } }) {
        if (state.exams.isEmpty()) {
            val waiting = state.sync?.latestExams.orEmpty().any { it.term.key == state.schedule?.term?.key }
            if (waiting) {
                Text("新的考试安排待确认，确认后会加入日程。")
                FilledTonalButton(onClick = { onDismiss(); onReview() }) { Text("查看教务更新") }
            } else Text("当前课表学期尚未发布考试安排。检查教务更新后会拉取最新信息。")
        }
        state.exams.sortedWith(compareBy({ it.date ?: "9999" }, { it.startTime })).forEach { exam ->
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(exam.name, style = MaterialTheme.typography.titleMedium)
                Text(exam.timeText); Text("地点：${exam.location ?: "待公布"}")
                exam.seat?.let { Text("座位：$it") }; exam.type?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                exam.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val start = exam.startsAt(); val end = exam.endsAt()
                if (start != null && end != null) {
                    val conflicts = courses.filter { it.startsAt < end && it.endsAt > start }.map { it.lesson.name } +
                        state.exams.filter { it.id != exam.id && (it.startsAt() ?: Long.MAX_VALUE) < end && (it.endsAt() ?: Long.MIN_VALUE) > start }.map { "考试：${it.name}" }
                    if (conflicts.isNotEmpty()) Text("时间重叠：${conflicts.distinct().joinToString("、")}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                exam.date?.let { date -> TextButton(onClick = { onDate(date); onDismiss() }) { Text("在课表中查看日期") } }
            } }
        }
        Text("考试提醒可在“提醒与课程实况”中开启。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WeekExamStrip(schedule: ScheduleData, week: Int, exams: List<ExamInfo>, onClick: () -> Unit) {
    val start = schedule.startDate?.let { runCatching { LocalDate.parse(it).plusWeeks((week - 1).toLong()) }.getOrNull() } ?: return
    val records = exams.filter { exam -> exam.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it >= start && it < start.plusDays(7) } == true }
    if (records.isEmpty()) return
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text("本周考试 · ${records.size} 场", style = MaterialTheme.typography.labelLarge)
            records.sortedBy { it.date + it.startTime }.take(2).forEach { Text("${it.date?.takeLast(5)} ${it.startTime ?: "时间待定"} · ${it.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            if (records.size > 2) Text("查看全部考试", style = MaterialTheme.typography.labelSmall)
        }
    }
}
