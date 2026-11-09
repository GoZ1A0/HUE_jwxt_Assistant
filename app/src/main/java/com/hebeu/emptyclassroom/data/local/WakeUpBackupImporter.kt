package com.hebeu.emptyclassroom.data.local

import com.hebeu.emptyclassroom.domain.model.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime

/**
 * WakeUp 五行备份：时间模板、节次数组、课表配置、课程基础数组、排课数组。
 * 全程解析数据，不执行备份中的路径、表达式或其他内容。
 */
object WakeUpBackupImporter {
    fun importBackup(text: String): ImportedTimetable {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.size == 5) { "请选择完整的 WakeUp 五行课表备份文件" }
        val values = try { lines.map { Json.parseToJsonElement(it) } }
        catch (e: Exception) { throw IllegalArgumentException("课表备份 JSON 格式有误", e) }
        val template = values[0] as? JsonObject ?: error("备份缺少时间模板")
        val times = values[1] as? JsonArray ?: error("备份缺少节次时间")
        val settings = values[2] as? JsonObject ?: error("备份缺少课表设置")
        val courses = values[3] as? JsonArray ?: error("备份缺少课程信息")
        val details = values[4] as? JsonArray ?: error("备份缺少排课信息")
        val name = settings.text("tableName") ?: "导入课表"
        val start = settings.text("startDate")?.let(::parseDate)
        val year = start?.let { if (it.monthValue >= 8) it.year else it.year - 1 }?.toString() ?: "local"
        val semester = start?.let { if (it.monthValue >= 8) "3" else "12" } ?: "local"
        val bases = courses.map { it.jsonObject }.associateBy { courseKey(it) }
        require(bases.size == courses.size) { "备份存在重复课程编号" }
        val colors = linkedMapOf<String, String>()
        val credits = linkedMapOf<String, String>()
        val customTimes = linkedMapOf<String, ImportedLessonTime>()
        var largestWeek = 1
        val lessons = details.mapIndexed { index, element ->
            val row = element as? JsonObject ?: error("第 ${index + 1} 条排课格式有误")
            val base = bases[courseKey(row)] ?: error("第 ${index + 1} 条排课找不到对应课程")
            val id = "wakeup-${row.text("tableId").orEmpty()}-${row.text("id").orEmpty()}-$index"
            val firstWeek = row.integer("startWeek", 1)
            val lastWeek = row.integer("endWeek", firstWeek)
            val firstPeriod = row.integer("startNode", 1)
            val length = row.integer("step", 1)
            val day = row.integer("day", 0)
            val type = row.integer("type", 0)
            require(firstWeek in 1..60 && lastWeek in firstWeek..60) { "${base.text("courseName")}的周次超出范围" }
            require(firstPeriod in 1..60 && length in 1..60 && firstPeriod + length - 1 <= 60) { "课程节次超出范围" }
            require(day in 1..7) { "课程星期应为 1 到 7" }
            require(type in 0..2) { "备份使用了未识别的周次类型 $type" }
            val weeks = (firstWeek..lastWeek).filter { week -> when (type) {
                1 -> week % 2 == 1
                2 -> week % 2 == 0
                else -> true
            } }.toSet()
            require(weeks.isNotEmpty()) { "${base.text("courseName")}的单双周范围为空" }
            largestWeek = maxOf(largestWeek, lastWeek)
            base.text("color")?.let { colors[id] = normalizeColor(it) }
            base.text("credit")?.let { credits[id] = it }
            val ownTime = row.flag("ownTime", false)
            val ownStart = row.text("startTime")?.let(::normalizeTime)
            val ownEnd = row.text("endTime")?.let(::normalizeTime)
            if (ownTime && ownStart != null && ownEnd != null) customTimes[id] = ImportedLessonTime(ownStart, ownEnd)
            val parity = when (type) { 1 -> "(单周)"; 2 -> "(双周)"; else -> "" }
            val weekText = (if (firstWeek == lastWeek) "${firstWeek}周" else "$firstWeek-${lastWeek}周") + parity
            val periods = (firstPeriod until firstPeriod + length).toList()
            val periodText = if (length == 1) "${firstPeriod}节" else "$firstPeriod-${periods.last()}节"
            val notes = listOfNotNull(base.text("note"), if (ownTime && ownStart != null && ownEnd != null) "自定义时间 $ownStart-$ownEnd" else null)
            Lesson(id, base.text("courseName") ?: "未命名课程", row.text("teacher"), row.text("room"), day,
                periods, weeks, weekText, periodText, notes.joinToString("\n").takeIf { it.isNotBlank() })
        }
        val templateId = template.text("id")
        val selectedTimes = times.map { it.jsonObject }.let { rows ->
            rows.filter { it.text("timeTable") == templateId }.ifEmpty { rows }
        }
        val periodTimes = selectedTimes.associate { row ->
            val node = row.integer("node", 0)
            require(node in 1..60) { "节次时间编号超出范围" }
            val from = normalizeTime(row.text("startTime") ?: error("节次缺少开始时间"))
            val to = normalizeTime(row.text("endTime") ?: error("节次缺少结束时间"))
            node to "$from-$to"
        }
        val style = ImportedTimetableStyle(
            backgroundReference = settings.text("background"),
            textColorArgb = settings.argb("textColor", 0xFF101820),
            courseTextColorArgb = settings.argb("courseTextColor", 0xFF101820),
            strokeColorArgb = settings.argb("strokeColor", 0),
            cardAlpha = settings.integer("itemAlpha", 100).coerceIn(0, 100) / 100f,
            rowHeightDp = settings.integer("itemHeight", 72).coerceIn(24, 200),
            textSizeSp = settings.integer("itemTextSize", 12).coerceIn(8, 32),
            nodes = settings.integer("nodes", 12).coerceIn(1, 60),
            showSaturday = settings.flag("showSat", true),
            showSunday = settings.flag("showSun", true),
            showTimes = settings.flag("showTime", true),
            showOtherWeeks = settings.flag("showOtherWeekCourse", false),
            sundayFirst = settings.flag("sundayFirst", false),
        )
        val schedule = ScheduleData(AcademicTerm(year, semester, name), lessons, System.currentTimeMillis(),
            start?.toString(), maxOf(settings.integer("maxWeek", 20).coerceIn(1, 60), largestWeek), periodTimes)
        return ImportedTimetable(name, schedule, colors, style, customTimes, credits)
    }

    private fun courseKey(row: JsonObject) = "${row.text("tableId").orEmpty()}/${row.text("id").orEmpty()}"
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    private fun JsonObject.integer(key: String, fallback: Int) = text(key)?.toIntOrNull() ?: fallback
    private fun JsonObject.flag(key: String, fallback: Boolean) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
    private fun JsonObject.argb(key: String, fallback: Long): Long = text(key)?.toLongOrNull()?.and(0xFFFFFFFFL) ?: fallback
    private fun parseDate(value: String): LocalDate {
        val match = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matchEntire(value)
            ?: throw IllegalArgumentException("开学日期格式有误")
        return LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }
    private fun normalizeTime(value: String): String {
        val match = Regex("(\\d{1,2}):(\\d{2})(?::\\d{2})?").matchEntire(value)
            ?: throw IllegalArgumentException("节次时间格式有误")
        return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()).toString()
    }
    private fun normalizeColor(value: String): String {
        val raw = value.removePrefix("#")
        require(raw.length == 6 || raw.length == 8) { "课程颜色格式有误" }
        require(raw.toLongOrNull(16) != null) { "课程颜色格式有误" }
        return "#" + (if (raw.length == 6) "ff$raw" else raw).lowercase()
    }
}
