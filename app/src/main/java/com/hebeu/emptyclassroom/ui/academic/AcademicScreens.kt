package com.hebeu.emptyclassroom.ui.academic

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebeu.emptyclassroom.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun timeText(value: Long) = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(value))

@Composable
fun TermPicker(terms: List<AcademicTerm>, selected: AcademicTerm?, allowAll: Boolean = false,
    onSelected: (AcademicTerm?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = terms.isNotEmpty()) {
            Text(selected?.label ?: "全部学期", Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, "选择学期")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            if (allowAll) DropdownMenuItem(text = { Text("全部学期") }, onClick = { expanded = false; onSelected(null) })
            terms.forEach { term -> DropdownMenuItem(text = { Text(term.label) }, onClick = {
                expanded = false; onSelected(term)
            }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRoute(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScheduleScreen(state, viewModel::selectTerm, viewModel::setWeek, viewModel::thisWeek,
        viewModel::toggleMode, viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(state: ScheduleState, onTerm: (AcademicTerm) -> Unit, onWeek: (Int) -> Unit,
    onToday: () -> Unit, onToggle: () -> Unit, onRefresh: () -> Unit) {
    var selected by remember { mutableStateOf<Lesson?>(null) }
    selected?.let { LessonDialog(it, state.data?.periodTimes.orEmpty(), onDismiss = { selected = null }) }
    var weekPicker by remember { mutableStateOf(false) }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("个人课表") }, actions = {
        IconButton(onClick = onToggle) { Icon(if (state.listMode) Icons.Outlined.CalendarViewWeek else Icons.AutoMirrored.Outlined.ViewList,
            if (state.listMode) "切换周课表" else "切换列表") }
        IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "刷新课表") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TermPicker(state.terms, state.selected, onSelected = { it?.let(onTerm) }) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onWeek(state.week - 1) }, enabled = state.week > 1) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { weekPicker = true }) { Text("第 ${state.week} 周"); Icon(Icons.Outlined.ExpandMore, null) }
                        DropdownMenu(weekPicker, onDismissRequest = { weekPicker = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                            (1..ScheduleViewModel.maxWeeks(state.data)).forEach { week ->
                                DropdownMenuItem(text = { Text("第 $week 周") }, onClick = { weekPicker = false; onWeek(week) })
                            }
                        }
                    }
                    TextButton(onClick = onToday) { Text("本周") }
                    IconButton(onClick = { onWeek(state.week + 1) }, enabled = state.week < ScheduleViewModel.maxWeeks(state.data)) {
                        Icon(Icons.Outlined.ChevronRight, "下一周")
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { ErrorCard(error, onRefresh) } }
            state.data?.let { data ->
                item { SyncLabel(data.syncedAt, state.cached) }
                val lessons = data.lessons.filter { it.scheduled && state.week in it.weeks }
                if (lessons.isEmpty()) item { Text("这一周没有已安排的课程", Modifier.padding(16.dp)) }
                if (!state.listMode) item { ScheduleGrid(data, state.week, onLesson = { selected = it }) }
                else items(lessons.sortedWith(compareBy({ it.weekday }, { it.periods.firstOrNull() })), key = { it.id }) { lesson ->
                    LessonCard(lesson, onClick = { selected = lesson })
                }
                val pending = data.lessons.filter { !it.scheduled }
                if (pending.isNotEmpty()) item { Text("待定时间 / 实践课程", style = MaterialTheme.typography.titleMedium) }
                items(pending, key = { "pending-${it.id}" }) { lesson -> LessonCard(lesson, onClick = { selected = lesson }) }
            }
        }
    }
}

@Composable
private fun ScheduleGrid(data: ScheduleData, week: Int, onLesson: (Lesson) -> Unit) {
    val lessons = data.lessons.filter { it.scheduled && week in it.weeks }
    val maxPeriod = maxOf(10, data.periodTimes.keys.maxOrNull() ?: 0,
        lessons.flatMap { it.periods }.maxOrNull() ?: 0).coerceAtMost(30)
    val start = data.startDate?.let { runCatching { LocalDate.parse(it).plusWeeks((week - 1).toLong()) }.getOrNull() }
    BoxWithConstraints {
        val dayWidth = ((maxWidth - 32.dp) / 7).coerceAtLeast(44.dp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(32.dp)) {
                Spacer(Modifier.height(48.dp))
                (1..maxPeriod).forEach { period -> Box(Modifier.height(62.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text(period.toString(), style = MaterialTheme.typography.labelMedium)
                } }
            }
            (1..7).forEach { day ->
                val today = LocalDate.now(ZoneId.of("Asia/Shanghai")) == start?.plusDays((day - 1).toLong())
                Column(Modifier.width(dayWidth)) {
                    Column(Modifier.height(48.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("周${"一二三四五六日"[day - 1]}", color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        start?.plusDays((day - 1).toLong())?.let { Text("${it.monthValue}/${it.dayOfMonth}", style = MaterialTheme.typography.labelSmall) }
                    }
                    val dayLessons = lessons.filter { it.weekday == day }.sortedBy { it.periods.min() }
                    val ends = mutableListOf<Int>()
                    val lanes = dayLessons.associate { lesson ->
                        var lane = ends.indexOfFirst { it < lesson.periods.min() }
                        if (lane == -1) { lane = ends.size; ends.add(0) }
                        ends[lane] = lesson.periods.max()
                        lesson.id to lane
                    }
                    val laneCount = ends.size.coerceAtLeast(1)
                    Box(Modifier.height((maxPeriod * 62).dp).fillMaxWidth()) {
                        Column { repeat(maxPeriod) { HorizontalDivider(); Spacer(Modifier.height(61.dp)) } }
                        dayLessons.forEach { lesson ->
                            val laneWidth = dayWidth / laneCount
                            Surface(onClick = { onLesson(lesson) },
                                modifier = Modifier.offset(x = laneWidth * (lanes[lesson.id] ?: 0), y = ((lesson.periods.min() - 1) * 62).dp)
                                    .width(laneWidth).height(((lesson.periods.max() - lesson.periods.min() + 1) * 62).dp).padding(2.dp),
                                shape = MaterialTheme.shapes.small,
                                color = if (lesson.id.hashCode() % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                                Column(Modifier.padding(5.dp)) {
                                    Text(lesson.name, style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    lesson.location?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(lesson.name, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(lesson.weekday?.takeIf { it in 1..7 }?.let { "周${"一二三四五六日"[it - 1]}" },
                lesson.periodText.takeIf { it.isNotBlank() }, lesson.location).joinToString(" · "))
            Text(listOfNotNull(lesson.teacher, lesson.weekText.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LessonDialog(lesson: Lesson, times: Map<Int, String>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(lesson.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("教师：${lesson.teacher ?: "待定"}")
            Text("地点：${lesson.location ?: "待定"}")
            Text("周次：${lesson.weekText.ifBlank { "待定" }}")
            Text("节次：${lesson.periodText.ifBlank { "待定" }}")
            Text(lesson.periods.mapNotNull { p -> times[p]?.let { "第${p}节 $it" } }.joinToString("\n"))
            lesson.note?.let { Text(it) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } })
}

@Composable
fun GradesRoute(viewModel: GradesViewModel, menuActions: com.hebeu.emptyclassroom.ui.settings.AppMenuActions? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GradesScreen(state, viewModel::selectTerm, viewModel::search, viewModel::refresh, menuActions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(state: GradesState, onTerm: (AcademicTerm?) -> Unit, onSearch: (String) -> Unit, onRefresh: () -> Unit,
    menuActions: com.hebeu.emptyclassroom.ui.settings.AppMenuActions? = null) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("绩点与成绩") }, actions = {
        IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "刷新绩点与成绩") }
        com.hebeu.emptyclassroom.ui.settings.AppOverflowMenu(menuActions ?: com.hebeu.emptyclassroom.ui.settings.AppMenuActions({}, {}, {}))
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { ErrorCard(error, onRefresh) } }
            state.data?.let { data ->
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("官方平均学分绩点", style = MaterialTheme.typography.titleMedium)
                            Text(data.officialGpa ?: "暂未公布", style = MaterialTheme.typography.displayMedium)
                            Text("累计 · 当前所有课程 · 学校学业情况统计", style = MaterialTheme.typography.bodySmall)
                            data.officialAverage?.let { Text("官方加权平均分  $it") }
                            Text("统计更新 ${timeText(data.overviewAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item { SyncLabel(data.syncedAt, state.cached) }
            }
            item { Text("课程成绩", style = MaterialTheme.typography.titleLarge) }
            item { TermPicker(state.terms, state.selected, allowAll = true, onSelected = onTerm) }
            item { OutlinedTextField(state.search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("搜索课程") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }) }
            val records = state.data?.records.orEmpty().filter { it.name.contains(state.search, ignoreCase = true) }
            if (state.data != null) item { Text("${records.size} 条记录 · 学期筛选仅作用于下方课程明细", style = MaterialTheme.typography.labelMedium) }
            if (state.data != null && records.isEmpty()) item { Text("暂无符合条件的课程成绩", Modifier.padding(16.dp)) }
            items(records, key = { it.id }) { grade ->
                OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text(grade.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(grade.score ?: "未公布", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("学分 ${grade.credit ?: "—"}   绩点 ${grade.gradePoint ?: "—"}   学分绩点 ${grade.weightedPoint ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                        Text(listOfNotNull(grade.term.takeIf { it.isNotBlank() }, grade.nature, grade.examType).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncLabel(at: Long, cached: Boolean) {
    Text((if (cached) "本地缓存 · " else "已同步 · ") + timeText(at) + " · 河北工程大学教务系统",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text("重试") } }
    }
}
