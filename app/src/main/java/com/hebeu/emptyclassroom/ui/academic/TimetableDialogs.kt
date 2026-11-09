package com.hebeu.emptyclassroom.ui.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import com.hebeu.emptyclassroom.domain.model.Lesson
import com.hebeu.emptyclassroom.domain.model.SchedulePatterns
import com.hebeu.emptyclassroom.domain.model.TimetableAppearance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableAppearanceDialog(
    value: TimetableAppearance,
    onChange: (TimetableAppearance) -> Unit,
    onWallpaperPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("课表样式") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("即时预览，关闭后保留修改", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pastel" to "柔和", "vivid" to "鲜艳", "material" to "主题").forEach { (key, label) ->
                    FilterChip(selected = value.palette == key, onClick = { onChange(value.copy(palette = key)) }, label = { Text(label) })
                }
            }
            AppearanceSlider("字体大小", value.textSizeSp, 9..22, "sp") { onChange(value.copy(textSizeSp = it)) }
            AppearanceSlider("每节高度", value.rowHeightDp, 44..150, "dp") { onChange(value.copy(rowHeightDp = it)) }
            AppearanceSlider("卡片圆角", value.cornerRadiusDp, 0..28, "dp") { onChange(value.copy(cornerRadiusDp = it)) }
            Text("卡片不透明度  ${(value.cardAlpha * 100).roundToInt()}%")
            Slider(value.cardAlpha.coerceIn(0.2f, 1f), onValueChange = { onChange(value.copy(cardAlpha = it)) }, valueRange = 0.2f..1f)
            AppearanceSlider("显示节数", value.nodes, 1..30, "节") { onChange(value.copy(nodes = it)) }
            HorizontalDivider()
            AppearanceToggle("显示教师", value.showTeacher) { onChange(value.copy(showTeacher = it)) }
            AppearanceToggle("显示地点", value.showLocation) { onChange(value.copy(showLocation = it)) }
            AppearanceToggle("显示节次时间", value.showTimes) { onChange(value.copy(showTimes = it)) }
            AppearanceToggle("显示周六", value.showSaturday) { onChange(value.copy(showSaturday = it)) }
            AppearanceToggle("显示周日", value.showSunday) { onChange(value.copy(showSunday = it)) }
            AppearanceToggle("显示非本周课程", value.showOtherWeeks) { onChange(value.copy(showOtherWeeks = it)) }
            AppearanceToggle("周日放在第一列", value.sundayFirst) { onChange(value.copy(sundayFirst = it)) }
            AppearanceToggle("课程文字加粗", value.boldText) { onChange(value.copy(boldText = it)) }
            AppearanceToggle("课程文字居中", value.centeredText) { onChange(value.copy(centeredText = it)) }
            HorizontalDivider()
            Text("课程文字颜色", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(value.courseTextColorArgb == null, { onChange(value.copy(courseTextColorArgb = null)) }, label = { Text("自动") })
                FilterChip(value.courseTextColorArgb == 0xFF111111L, { onChange(value.copy(courseTextColorArgb = 0xFF111111L)) }, label = { Text("深色") })
                FilterChip(value.courseTextColorArgb == 0xFFFFFFFFL, { onChange(value.copy(courseTextColorArgb = 0xFFFFFFFFL)) }, label = { Text("白色") })
            }
            Text("背景", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onWallpaperPick, modifier = Modifier.fillMaxWidth()) { Text(if (value.wallpaperPath == null) "选择背景图片" else "更换背景图片") }
            if (value.wallpaperPath != null) TextButton(onClick = { onChange(value.copy(wallpaperPath = null)) }) { Text("清除背景图片") }
            TextButton(onClick = { onChange(TimetableAppearance()) }) { Text("重置课表样式") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun AppearanceSlider(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Text("$label  $value $unit")
        Slider(value.coerceIn(range).toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

@Composable
private fun AppearanceToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditorDialog(
    initial: Lesson?,
    initialColor: String?,
    totalWeeks: Int,
    onSave: (Lesson, String?) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var teacher by remember(initial?.id) { mutableStateOf(initial?.teacher.orEmpty()) }
    var location by remember(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var day by remember(initial?.id) { mutableIntStateOf(initial?.weekday?.takeIf { it in 1..7 } ?: 1) }
    var first by remember(initial?.id) { mutableStateOf((initial?.periods?.minOrNull() ?: 1).toString()) }
    var last by remember(initial?.id) { mutableStateOf((initial?.periods?.maxOrNull() ?: 2).toString()) }
    var weekText by remember(initial?.id) { mutableStateOf(initial?.weekText?.takeIf { it.isNotBlank() } ?: "1-${totalWeeks.coerceAtLeast(1)}周") }
    var parity by remember(initial?.id) { mutableIntStateOf(when {
        initial?.weekText?.contains("单") == true && initial.weeks.all { it % 2 == 1 } -> 1
        initial?.weekText?.contains("双") == true && initial.weeks.all { it % 2 == 0 } -> 2
        else -> 0
    }) }
    var color by remember(initial?.id) { mutableStateOf(initialColor.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = listOf("#fffa6278", "#ff80aef7", "#ff66d9c9", "#ffb399ed", "#ffffbf70", "#ffef967f", "#ff83bfd7", "#ff82c99d")
    fun save() {
        val start = first.toIntOrNull()
        val end = last.toIntOrNull()
        if (name.isBlank()) { error = "请输入课程名称"; return }
        if (day !in 1..7) { error = "请选择星期"; return }
        if (start == null || end == null || start !in 1..30 || end !in start..30) { error = "节次应为 1–30，结束节次须不早于开始节次"; return }
        if (!Regex("[\\d\\s,，、;；\\-－—~至周第()（）单双]+").matches(weekText)) { error = "请按 1-8,10,12-16周 的格式填写周次"; return }
        val numbers = Regex("\\d+").findAll(weekText).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.isEmpty() || numbers.any { it !in 1..totalWeeks.coerceIn(1, 60) }) { error = "周次应在 1–${totalWeeks.coerceIn(1, 60)} 周内"; return }
        val weeks = SchedulePatterns.weeks(weekText).filter { when (parity) { 1 -> it % 2 == 1; 2 -> it % 2 == 0; else -> true } }.toSet()
        if (weeks.isEmpty()) { error = "没有符合周次与单双周条件的课程日期"; return }
        val normalizedColor = color.trim().takeIf { it.isNotBlank() }?.let {
            val digits = it.removePrefix("#")
            if ((digits.length != 6 && digits.length != 8) || digits.toLongOrNull(16) == null) { error = "颜色请填写 #RRGGBB 或 #AARRGGBB"; return }
            "#${if (digits.length == 6) "ff$digits" else digits}".lowercase()
        }
        val description = if (parity == 0) weekText else weekText.replace(Regex("[（(][单双](?:周)?[）)]"), "") +
            if (parity == 1) "(单周)" else "(双周)"
        val periods = if (initial != null && start == initial.periods.minOrNull() && end == initial.periods.maxOrNull()) initial.periods else (start..end).toList()
        onSave(Lesson(initial?.id ?: "manual-${UUID.randomUUID()}", name.trim(), teacher.trim().takeIf { it.isNotBlank() },
            location.trim().takeIf { it.isNotBlank() }, day, periods, weeks, description,
            if (start == end) "${start}节" else "$start-${end}节", note.trim().takeIf { it.isNotBlank() },
            initial?.courseCode, initial?.credit, initial?.nature), normalizedColor)
        onDismiss()
    }
    val dialogHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = dialogHeight),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp)) {
        Text(if (initial == null) "添加课程" else "编辑课程", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, onValueChange = { name = it; error = null }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(teacher, onValueChange = { teacher = it }, label = { Text("教师") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(location, onValueChange = { location = it }, label = { Text("上课地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("星期", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { value -> FilterChip(day == value, { day = value }, label = { Text("周${"一二三四五六日"[value - 1]}") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, onValueChange = { first = it }, label = { Text("开始节次") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(last, onValueChange = { last = it }, label = { Text("结束节次") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
            OutlinedTextField(weekText, onValueChange = { weekText = it; error = null }, label = { Text("上课周次") },
                placeholder = { Text("1-8,10,12-16周") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "每周", 1 to "单周", 2 to "双周").forEach { (value, title) -> FilterChip(parity == value,
                    { parity = value; weekText = weekText.replace(Regex("[（(][单双](?:周)?[）)]"), "") }, label = { Text(title) }) }
            }
            Text("课程颜色", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEachIndexed { index, hex ->
                    Box(Modifier.size(32.dp).background(Color(hex.drop(1).toLong(16)), CircleShape)
                        .border(if (color.equals(hex, true)) 3.dp else 1.dp,
                            if (color.equals(hex, true)) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .clickable { color = hex }.semantics { contentDescription = "课程颜色 ${index + 1}" })
                }
            }
            OutlinedTextField(color, onValueChange = { color = it }, label = { Text("自定义颜色，留空自动配色") },
                placeholder = { Text("#80AEEF") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (initial != null) TextButton(onClick = { onDelete(initial.id); onDismiss() }) {
                Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("删除这条排课", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(onClick = ::save) { Text("保存") }
        }
        }
        }
    }
}

@Composable
fun TimetableManagerDialog(
    state: TimetableState,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onClone: () -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onCalendar: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("课表管理") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onSelect("school"); onDismiss() }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.workspace.activeId == "school", onClick = { onSelect("school"); onDismiss() })
                Column(Modifier.weight(1f)) { Text("教务课表", style = MaterialTheme.typography.titleSmall); Text("与学校系统同步", style = MaterialTheme.typography.bodySmall) }
            }
            state.workspace.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(state.workspace.activeId == profile.id, onClick = { onSelect(profile.id); onDismiss() })
                    Column(Modifier.weight(1f).clickable { onSelect(profile.id); onDismiss() }) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("本地 · ${profile.schedule.lessons.map { it.name }.distinct().size} 门课程", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDelete(profile.id) }) { Icon(Icons.Outlined.Delete, "删除课表 ${profile.name}") }
                }
            }
            HorizontalDivider()
            OutlinedButton(onClick = { onCreate(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("新建空白课表") }
            OutlinedButton(onClick = { onClone(); onDismiss() }, enabled = state.schedule != null, modifier = Modifier.fillMaxWidth()) { Text("复制当前课表") }
            OutlinedButton(onClick = { onImport(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("导入课表备份") }
            OutlinedButton(onClick = { onCalendar(); onDismiss() }, enabled = state.schedule != null, modifier = Modifier.fillMaxWidth()) { Text("课表名称、校历与作息") }
            if (state.local == null) Text("编辑教务课表时，修改会保存为本地副本。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
fun TimetableCalendarDialog(state: TimetableState, onSave: (String, String, Int, Map<Int, String>) -> Unit, onDismiss: () -> Unit) {
    val key = state.workspace.activeId
    var name by remember(key) { mutableStateOf(state.name) }
    var startDate by remember(key) { mutableStateOf(state.schedule?.startDate.orEmpty()) }
    var totalWeeks by remember(key) { mutableStateOf((state.schedule?.totalWeeks ?: 20).toString()) }
    var periods by remember(key) { mutableStateOf(state.schedule?.periodTimes?.filterValues { it != "00:00-00:00" }?.toSortedMap()?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save() {
        if (name.isBlank()) { error = "请输入课表名称"; return }
        val date = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull()
        if (date == null) { error = "开学日期请填写 YYYY-MM-DD，例如 2026-08-31"; return }
        if (date.dayOfWeek != DayOfWeek.MONDAY) { error = "第一周起始日期应为周一"; return }
        val weeks = totalWeeks.toIntOrNull()
        if (weeks == null || weeks !in 1..60) { error = "总周数应为 1–60"; return }
        val result = linkedMapOf<Int, String>()
        val lines = periods.lines().map(String::trim).filter(String::isNotBlank)
        if (lines.isEmpty()) { error = "请至少填写一节课程的作息时间"; return }
        for ((index, line) in lines.withIndex()) {
            val match = Regex("(\\d{1,2})\\s*=\\s*(\\d{1,2}:\\d{2})\\s*[-－—]\\s*(\\d{1,2}:\\d{2})").matchEntire(line)
            if (match == null) { error = "第 ${index + 1} 行应为 1=08:30-09:15 的格式"; return }
            val node = match.groupValues[1].toInt()
            if (node !in 1..60 || node in result) { error = "节次编号应为 1–60，且不能重复"; return }
            fun time(value: String): LocalTime? = runCatching {
                val parts = value.split(':'); LocalTime.of(parts[0].toInt(), parts[1].toInt())
            }.getOrNull()
            val from = time(match.groupValues[2]); val to = time(match.groupValues[3])
            if (from == null || to == null || !to.isAfter(from)) { error = "第 ${index + 1} 行的时间无效，结束时间须晚于开始时间"; return }
            result[node] = "$from-$to"
        }
        val missing = state.schedule?.lessons.orEmpty().flatMap { it.periods }.distinct().filterNot { it in result }.sorted()
        if (missing.isNotEmpty()) { error = "请补充课程使用的第 ${missing.joinToString("、")} 节时间"; return }
        val lastCourseWeek = state.schedule?.lessons.orEmpty().flatMap { it.weeks }.maxOrNull() ?: 1
        if (weeks < lastCourseWeek) { error = "已有课程安排至第 $lastCourseWeek 周，请保留足够的总周数"; return }
        onSave(name.trim(), date.toString(), weeks, result)
        onDismiss()
    }
    val dialogHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = dialogHeight),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp)) {
        Text("课表校历与作息", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, onValueChange = { name = it; error = null }, label = { Text("课表名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(startDate, onValueChange = { startDate = it; error = null }, label = { Text("第一周周一日期") },
                placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(totalWeeks, onValueChange = { totalWeeks = it; error = null }, label = { Text("总周数") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(periods, onValueChange = { periods = it; error = null }, label = { Text("每节时间，一行一节") },
                placeholder = { Text("1=08:30-09:15\n2=09:20-10:05") }, modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10)
            Text("按你的实际作息填写；已有课程使用的节次需要保留时间。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(onClick = ::save) { Text("保存") }
        }
        }
        }
    }
}
