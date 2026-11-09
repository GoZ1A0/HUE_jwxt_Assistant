package com.hebeu.emptyclassroom.ui.academic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.testTag
import com.hebeu.emptyclassroom.ui.settings.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebeu.emptyclassroom.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun TimetableRoute(viewModel: TimetableViewModel, onAccount: () -> Unit, onLogout: () -> Unit,
    menuActions: AppMenuActions = AppMenuActions(onAccount, {}, {}), settingsPage: Boolean = false, onBack: () -> Unit = {}, handleReview: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manage by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var exams by remember { mutableStateOf(false) }
    var widgets by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<Lesson?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshReminderStatus() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshReminderStatus() }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        try { val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("文件读取失败") }
            viewModel.importText(text)
        } catch (e: Exception) { viewModel.message("导入失败：${e.message.orEmpty()}") }
    } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) scope.launch {
        try { val text = viewModel.exportText(); withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } ?: error("文件写入失败") }
            viewModel.message("课表已导出")
        } catch (e: Exception) { viewModel.message("导出失败：${e.message.orEmpty()}") }
    } }
    val wallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        try { val path = withContext(Dispatchers.IO) {
            val folder = File(context.noBackupFilesDir, "timetable-backgrounds").apply { mkdirs() }
            val file = File(folder, "${UUID.randomUUID()}.image")
            context.contentResolver.openInputStream(uri)?.use { source -> file.outputStream().use { source.copyTo(it) } } ?: error("图片读取失败")
            file.absolutePath
        }; viewModel.appearance(state.appearance.copy(wallpaperPath = path))
        } catch (e: Exception) { viewModel.message("背景读取失败：${e.message.orEmpty()}") }
    } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshReminderStatus() }
    if (state.showUpdateReview && handleReview) AcademicReviewDialog(state, viewModel::acceptUpdates, viewModel::deferUpdates)
    if (history) AcademicHistoryDialog(state, viewModel::reviewUpdates, viewModel::undoUpdate) { history = false }
    if (exams) AcademicExamsDialog(state, viewModel::date, onDismiss = { exams = false }, onReview = viewModel::reviewUpdates)
    if (widgets) TimetableWidgetDialog { widgets = false }
    val pickImport = { import.launch(arrayOf("*/*")) }
    if (manage) TimetableManagerDialog(state, viewModel::selectProfile,
        onCreate = { viewModel.create(); manage = false; calendar = true },
        onClone = { viewModel.create("${state.name}副本", true); manage = false }, onImport = pickImport,
        onDelete = viewModel::deleteProfile, onCalendar = { manage = false; calendar = true }, onDismiss = { manage = false })
    if (style) TimetableAppearanceDialog(state.appearance, viewModel::appearance,
        onWallpaperPick = { wallpaper.launch(arrayOf("image/*")) }, onDismiss = { style = false })
    if (calendar) TimetableCalendarDialog(state, viewModel::updateCalendar, onDismiss = { calendar = false })
    if (editing) CourseEditorDialog(initial, initial?.let { state.colors[it.id] }, ScheduleViewModel.maxWeeks(state.schedule),
        onSave = { lesson, color -> viewModel.saveLesson(lesson, color); editing = false },
        onDelete = { viewModel.deleteLesson(it); editing = false }, onDismiss = { editing = false })
    if (reminders) TimetableReminderDialog(state.workspace.reminders, state.reminderStatus,
        onChange = { value -> viewModel.reminderSettings(value)
            if (value.active && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, onNotificationSettings = { settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
        onExactSettings = { if (Build.VERSION.SDK_INT >= 31) settings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) },
        onDismiss = { reminders = false })
    if (settingsPage) TimetableSettingsScreen(state, viewModel::startupSyncEnabled,
        onManage = { manage = true }, onCalendar = { calendar = true }, onStyle = { style = true },
        onReminders = { reminders = true }, onToggleList = viewModel::toggleList, onSync = viewModel::checkUpdates,
        onHistory = { history = true }, onExams = { exams = true }, onImport = pickImport,
        onExport = { if (state.schedule != null) export.launch("${state.name}.json") }, onBack = onBack,
        onWidgets = { widgets = true })
    else TimetableHome(state, onWeek = viewModel::week, onToday = viewModel::today,
        onManage = { manage = true }, onStyle = { style = true }, onReminders = { reminders = true },
        onAccount = onAccount, onLogout = onLogout, onImport = pickImport,
        onExport = { if (state.schedule != null) export.launch("${state.name}.json") },
        onAdd = { initial = null; editing = true }, onEdit = { initial = it; editing = true },
        onSync = viewModel::checkUpdates, onToggleList = viewModel::toggleList,
        onDismissNotice = viewModel::dismissNotice, onReview = viewModel::reviewUpdates,
        onHistory = { history = true }, onExams = { exams = true }, menuActions = menuActions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableHome(state: TimetableState, onWeek: (Int) -> Unit, onToday: () -> Unit,
    onManage: () -> Unit, onStyle: () -> Unit, onReminders: () -> Unit,
    onAccount: () -> Unit, onLogout: () -> Unit, onImport: () -> Unit, onExport: () -> Unit,
    onAdd: () -> Unit, onEdit: (Lesson) -> Unit, onSync: () -> Unit, onToggleList: () -> Unit,
    onDismissNotice: () -> Unit, onReview: () -> Unit = {}, onHistory: () -> Unit = {}, onExams: () -> Unit = {},
    menuActions: AppMenuActions = AppMenuActions(onAccount, {}, {})) {
    val appearance = state.appearance
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val boardScroll = rememberScrollState()
    var weekPicker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Lesson?>(null) }
    var overlapping by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    val textColor = timetableHeaderColor(appearance)
    Box(Modifier.fillMaxSize()) {
        TimetableBackdrop(appearance)
        Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(title = {
                Column {
                    Text("${today.year}/${today.monthValue}/${today.dayOfMonth}", style = MaterialTheme.typography.headlineSmall, color = textColor)
                    Text("第${state.week}周  周${"一二三四五六日"[today.dayOfWeek.value - 1]}", style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }, actions = {
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "添加课程", tint = textColor) }
                IconButton(onClick = onImport) { Icon(Icons.Outlined.FileDownload, "导入课表", tint = textColor) }
                AppOverflowMenu(menuActions, textColor)
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().testTag("week-controls").weekSwipe(state.week, state.maxWeek, onWeek = onWeek).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onManage, modifier = Modifier.weight(1f)) {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onWeek(state.week - 1) }, enabled = state.week > 1) { Icon(Icons.Outlined.ChevronLeft, "上一周", tint = textColor) }
                    Box {
                        TextButton(onClick = { weekPicker = true }) { Text("第${state.week}周") }
                        DropdownMenu(weekPicker, onDismissRequest = { weekPicker = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                            (1..state.maxWeek).forEach { week ->
                                DropdownMenuItem(text = { Text("第 $week 周") }, onClick = { weekPicker = false; onWeek(week) })
                            }
                        }
                    }
                    IconButton(onClick = { onWeek(state.week + 1) }, enabled = state.week < state.maxWeek) { Icon(Icons.Outlined.ChevronRight, "下一周", tint = textColor) }
                    TextButton(onClick = onToday) { Text("本周") }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.sync?.pending == true) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("有待确认的教务更新", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = onReview) { Text("查看") }
                    }
                }
                state.syncWarning?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                state.error?.let { error ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onSync) { Text("重试") }
                    }
                }
                state.notice?.let { notice -> Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = onDismissNotice) { Icon(Icons.Outlined.Close, "关闭提示", Modifier.size(16.dp)) }
                    }
                } }
                if (state.cached) Text("离线课表 · 显示上次同步数据", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                val data = state.schedule
                if (data == null && !state.loading) Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("导入课表，或同步教务课表")
                    FilledTonalButton(onClick = onImport) { Text("导入 WakeUp 备份") }
                    TextButton(onClick = onSync) { Text("手动同步课表") }
                }
                data?.let {
                    WeekExamStrip(data, state.week, state.exams, onExams)
                    if (state.listMode) LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val records = data.lessons.filter { !it.scheduled || state.week in it.weeks || appearance.showOtherWeeks }
                        items(records.sortedWith(compareBy({ it.weekday }, { it.periods.firstOrNull() })), key = { it.id }) { lesson ->
                            OutlinedCard(onClick = { detail = lesson }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(lesson.name, style = MaterialTheme.typography.titleMedium)
                                    Text(listOfNotNull(lesson.location, lesson.teacher).joinToString(" · "))
                                    Text("${lesson.weekText} · ${lesson.periodText}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else WeekPager(state.week, state.maxWeek,
                        Modifier.weight(1f).fillMaxWidth().testTag("week-board"), onWeek) { displayedWeek ->
                            TimetableBoard(data, displayedWeek, appearance, state.colors, Modifier.fillMaxSize(),
                                onLesson = { if (displayedWeek == state.week) detail = it },
                                onOverlap = { if (displayedWeek == state.week) overlapping = it }, exams = state.exams,
                                onExamDay = onExams, scrollState = boardScroll, fitToViewport = true)
                    }
                }
            }
        }
    }
    detail?.let { lesson -> AcademicDialog(lesson.name, onDismiss = { detail = null }, actions = {
        TextButton(onClick = { detail = null }) { Text("关闭") }
        TextButton(onClick = { detail = null; onEdit(lesson) }) { Text("编辑课程") }
    }) {
            Text("${lesson.weekText} · ${lesson.periodText}")
            Text("地点：${lesson.location ?: "待定"}")
            Text("教师：${lesson.teacher ?: "待定"}")
            lesson.courseCode?.let { Text("课程编号：$it", style = MaterialTheme.typography.bodySmall) }
            val records = state.schedule?.term?.let { AcademicChanges.gradesFor(lesson, it, state.grades) }.orEmpty()
            Text("学分：${lesson.credit ?: state.local?.credits?.get(lesson.id) ?: records.firstOrNull()?.credit ?: "待公布"}")
            (lesson.nature ?: records.firstOrNull()?.nature)?.let { Text("课程性质：$it") }
            if (records.isEmpty()) Text("成绩 / 单科绩点：未公布", style = MaterialTheme.typography.bodyMedium)
            records.forEach { record -> Text("${record.examType.orEmpty()} · 成绩 ${record.score ?: "未公布"} · 单科绩点 ${record.gradePoint ?: "未公布"}") }
            AcademicChanges.examsFor(lesson, state.exams).forEach { exam ->
                HorizontalDivider(); Text("考试：${exam.timeText}"); Text("考场：${exam.location ?: "待公布"}${exam.seat?.let { " · 座位 $it" }.orEmpty()}")
            }
            state.lessonTimes[lesson.id]?.let { Text("时间：${it.start}–${it.end}") }
            lesson.note?.let { Text(it) }
    } }
    if (overlapping.isNotEmpty()) AlertDialog(onDismissRequest = { overlapping = emptyList() },
        title = { Text("该时段有 ${overlapping.size} 门课程") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                overlapping.forEach { lesson -> OutlinedCard(onClick = { detail = lesson; overlapping = emptyList() }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(lesson.name, style = MaterialTheme.typography.titleSmall)
                        Text("${lesson.weekText} · ${lesson.periodText}", style = MaterialTheme.typography.bodySmall)
                        Text(listOfNotNull(lesson.location, lesson.teacher).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
        }, confirmButton = { TextButton(onClick = { overlapping = emptyList() }) { Text("关闭") } })
}

@Composable
private fun TimetableBackdrop(appearance: TimetableAppearance) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    val image by produceState<ImageBitmap?>(null, appearance.wallpaperPath) {
        value = withContext(Dispatchers.IO) { appearance.wallpaperPath?.let { path -> runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1600).coerceAtLeast(1) }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }.getOrNull() } }
    }
    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

private data class BoardBlock(val lesson: Lesson, val start: Int, val end: Int, val alternatives: List<Lesson> = emptyList())
@Composable
private fun timetableHeaderColor(appearance: TimetableAppearance): Color {
    val custom = appearance.textColorArgb?.let { Color(it) }
    val background = MaterialTheme.colorScheme.background
    return custom?.takeUnless { appearance.wallpaperPath == null && kotlin.math.abs(it.luminance() - background.luminance()) < 0.25f }
        ?: MaterialTheme.colorScheme.onSurface
}
private fun blocks(lesson: Lesson): List<BoardBlock> {
    val periods = lesson.periods.distinct().sorted()
    if (periods.isEmpty()) return emptyList()
    val result = mutableListOf<BoardBlock>()
    var first = periods.first(); var last = first
    periods.drop(1).forEach { p -> if (p != last + 1) { result += BoardBlock(lesson, first, last); first = p }; last = p }
    result += BoardBlock(lesson, first, last)
    return result
}

@Composable
fun TimetableBoard(data: ScheduleData, week: Int, appearance: TimetableAppearance, colors: Map<String, String>,
    modifier: Modifier = Modifier, onLesson: (Lesson) -> Unit, onOverlap: (List<Lesson>) -> Unit = { onLesson(it.first()) },
    exams: List<ExamInfo> = emptyList(), onExamDay: () -> Unit = {}, scrollState: ScrollState? = null,
    fitToViewport: Boolean = false) {
    val days = (if (appearance.sundayFirst) listOf(7, 1, 2, 3, 4, 5, 6) else (1..7).toList())
        .filter { (it != 6 || appearance.showSaturday) && (it != 7 || appearance.showSunday) }
    val lessons = data.lessons.filter { it.scheduled && (week in it.weeks || appearance.showOtherWeeks) }
    val rows = maxOf(appearance.nodes, data.lessons.flatMap { it.periods }.maxOrNull() ?: 1).coerceIn(1, 30)
    val rowHeight = appearance.rowHeightDp.coerceIn(44, 150).dp
    val start = data.startDate?.let { runCatching { LocalDate.parse(it).plusWeeks((week - 1).toLong()) }.getOrNull() }
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val headerColor = timetableHeaderColor(appearance)
    val palettes = when (appearance.palette) {
        "vivid" -> listOf(0xFFF29AB2, 0xFF8DB6FB, 0xFF70DFCB, 0xFFB8A1EE, 0xFFF2BB80, 0xFF83B7D4)
        "material" -> emptyList()
        else -> listOf(0xFFC5D9F8, 0xFFBCE7DC, 0xFFE9C4D5, 0xFFD5CDF1, 0xFFF0D4B3, 0xFFBFDDEB)
    }
    val material = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val axis = if (appearance.showTimes) 42.dp else 28.dp
        val available = ((maxWidth - axis) / days.size).coerceAtLeast(1.dp)
        val dayWidth = if (fitToViewport) available else available.coerceAtLeast(42.dp)
        val columnsScroll = rememberScrollState()
        Column(Modifier.then(if (!fitToViewport && dayWidth > available) Modifier.horizontalScroll(columnsScroll) else Modifier)
            .width(axis + dayWidth * days.size).fillMaxHeight()) {
            Row(Modifier.height(52.dp)) {
                Box(Modifier.width(axis).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(start?.let { "${it.monthValue}\n月" } ?: "节次", color = headerColor, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
                days.forEach { day ->
                    val date = start?.plusDays((day - 1).toLong())
                    val isToday = date == today
                    val examCount = exams.count { it.date == date?.toString() }
                    Column(Modifier.width(dayWidth).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else if (examCount > 0) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
                        .then(if (examCount > 0) Modifier.clickable(onClick = onExamDay) else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("${"一二三四五六日"[day - 1]}", color = if (isToday) MaterialTheme.colorScheme.primary else headerColor, style = MaterialTheme.typography.titleSmall)
                        date?.let { Text("${it.monthValue}/${it.dayOfMonth}", color = headerColor, style = MaterialTheme.typography.labelSmall) }
                        if (examCount > 0) Text("考试 $examCount", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 9.sp)
                    }
                }
            }
            Box(Modifier.weight(1f).verticalScroll(scrollState ?: rememberScrollState())) {
                Row {
                    Column(Modifier.width(axis)) {
                        (1..rows).forEach { period -> Column(Modifier.height(rowHeight).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                            Text(period.toString(), color = headerColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (appearance.showTimes) data.periodTimes[period]?.takeIf { it != "00:00-00:00" }?.split('-')?.forEach {
                                Text(it, color = headerColor.copy(alpha = 0.75f), fontSize = 9.sp)
                            }
                        } }
                    }
                    days.forEach { day ->
                        val entries = lessons.filter { it.weekday == day }.flatMap(::blocks).groupBy { it.start }
                            .values.map { group ->
                                val primary = group.firstOrNull { week in it.lesson.weeks } ?: group.first()
                                primary.copy(alternatives = group.map { it.lesson }.distinctBy { it.id })
                            }.sortedBy { it.start }
                        val ends = mutableListOf<Int>()
                        val lanes = entries.map { block ->
                            var lane = ends.indexOfFirst { it < block.start }
                            if (lane < 0) { lane = ends.size; ends.add(0) }; ends[lane] = block.end
                            block to lane
                        }
                        val laneWidth = dayWidth / ends.size.coerceAtLeast(1)
                        Box(Modifier.width(dayWidth).height(rowHeight * rows)) {
                            lanes.forEach { (block, lane) ->
                                val lesson = block.lesson
                                val hex = colors[lesson.id] ?: colors["name:${lesson.name}"]
                                val index = Math.floorMod(lesson.name.hashCode(), if (palettes.isEmpty()) material.size else palettes.size)
                                val color = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                    ?: if (palettes.isEmpty()) material[index] else Color(palettes[index])
                                val isThisWeek = week in lesson.weeks
                                val foreground = appearance.courseTextColorArgb?.let { Color(it) }
                                    ?: if (palettes.isEmpty() && hex == null) MaterialTheme.colorScheme.onSurface else Color(0xFF192537)
                                Box(Modifier.offset(x = laneWidth * lane, y = rowHeight * (block.start - 1))
                                    .width(laneWidth).height(rowHeight * (block.end - block.start + 1)).padding(2.dp)
                                    .clip(RoundedCornerShape(appearance.cornerRadiusDp.dp))
                                    .background(color.copy(alpha = appearance.cardAlpha * if (isThisWeek) 1f else 0.22f))
                                    .clickable { if (block.alternatives.size > 1) onOverlap(block.alternatives) else onLesson(lesson) }
                                    .padding(horizontal = 4.dp, vertical = 6.dp)) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = if (appearance.centeredText) Alignment.CenterHorizontally else Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        val body = buildString {
                                            if (!isThisWeek) append("[非本周] ")
                                            append(lesson.name)
                                            if (appearance.showLocation && !lesson.location.isNullOrBlank()) append("\n@${lesson.location}")
                                        }
                                        Text(body, color = foreground, fontSize = appearance.textSizeSp.sp,
                                            fontWeight = if (appearance.boldText) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = if (appearance.centeredText) TextAlign.Center else TextAlign.Start,
                                            lineHeight = (appearance.textSizeSp * 1.25f).sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (appearance.showTeacher && !lesson.teacher.isNullOrBlank()) Text(lesson.teacher, color = foreground.copy(alpha = 0.85f),
                                            fontSize = (appearance.textSizeSp - 1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (block.alternatives.size > 1) Surface(color = foreground.copy(alpha = 0.14f), shape = RoundedCornerShape(5.dp),
                                        modifier = Modifier.align(Alignment.BottomEnd)) {
                                        Text("+${block.alternatives.size - 1}", Modifier.padding(horizontal = 3.dp), color = foreground,
                                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
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
private fun TimetableReminderDialog(value: ReminderSettings, status: String, onChange: (ReminderSettings) -> Unit,
    onNotificationSettings: () -> Unit, onExactSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("提醒与课程实况") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReminderSwitch("上课提醒", value.enabled) { onChange(value.copy(enabled = it)) }
            Text("提前 ${value.minutesBefore} 分钟")
            Slider(value.minutesBefore.toFloat(), onValueChange = { onChange(value.copy(minutesBefore = it.toInt())) }, valueRange = 0f..60f, steps = 11)
            ReminderSwitch("下课提醒", value.endEnabled) { onChange(value.copy(endEnabled = it)) }
            Text("下课前 ${value.minutesBeforeEnd} 分钟")
            Slider(value.minutesBeforeEnd.toFloat(), onValueChange = { onChange(value.copy(minutesBeforeEnd = it.toInt())) }, valueRange = 0f..10f, steps = 9)
            ReminderSwitch("课程实况 / 流体云适配", value.ongoingEnabled) { onChange(value.copy(ongoingEnabled = it)) }
            ReminderSwitch("考试提醒", value.examEnabled) { onChange(value.copy(examEnabled = it)) }
            if (value.examEnabled) {
                ReminderSwitch("考试前一天 20:00 提醒", value.examDayBefore) { onChange(value.copy(examDayBefore = it)) }
                Text("考前 ${value.examMinutesBefore} 分钟")
                Slider(value.examMinutesBefore.toFloat(), onValueChange = { onChange(value.copy(examMinutesBefore = it.toInt())) }, valueRange = 0f..180f, steps = 11)
            }
            Text("通知栏显示当前与即将开始的课程；系统支持时申请实况展示。", style = MaterialTheme.typography.bodySmall)
            ReminderSwitch("使用精确提醒", value.exactTiming) { onChange(value.copy(exactTiming = it)) }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onNotificationSettings) { Text("通知权限与展示设置") }
            if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = onExactSettings) { Text("精确提醒权限") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun ReminderSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = onChange)
    }
}
