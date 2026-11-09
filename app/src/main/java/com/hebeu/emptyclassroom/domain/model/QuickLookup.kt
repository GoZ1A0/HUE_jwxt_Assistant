package com.hebeu.emptyclassroom.domain.model

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class RoomAvailability(val until: LocalDateTime, val atLeast: Boolean = false) {
    fun minutesFrom(now: LocalDateTime): Long = ChronoUnit.MINUTES.between(now, until).coerceAtLeast(0)
    val label: String get() = "${if (atLeast) "至少" else ""}至 ${until.toLocalTime()}"
}
fun Classroom.lookupKey(): String = id.ifBlank { "${buildingName.orEmpty()}|$name" }

data class QuickLookupPlan(
    val week: Int,
    val weekday: Int,
    val period: PeriodOption,
    val isBetweenClasses: Boolean,
) {
    val description: String get() = "第${week}周 · 周${"一二三四五六日"[weekday - 1]} · 第${period.number}节 ${period.timeRange.orEmpty()}" +
        if (isBetweenClasses) "（课前/课间，按下一节）" else "（当前时段）"
}

/** 使用新校区服务端校历、作息；区间左闭右开，课间选下一节。 */
object QuickLookupPlanner {
    fun plan(options: QueryOptions, periods: List<PeriodOption>, now: LocalDateTime): QuickLookupPlan {
        val start = options.termInfo.startDate ?: error("学校暂未提供学期校历，请使用条件查询")
        val date = now.toLocalDate()
        val days = ChronoUnit.DAYS.between(start, date)
        val week = (days / 7).toInt() + 1
        check(days >= 0 && week <= options.termInfo.totalWeeks &&
            options.termInfo.endDate?.let { !date.isAfter(it) } != false) { "当前日期不在本学期校历内，请使用条件查询" }
        check(options.weeks.none { it.week == week && !it.selectable }) { "本周暂未开放查询" }
        val times = periods.mapNotNull { period ->
            val parts = period.timeRange?.replace('－', '-')?.replace('—', '-')?.split('-') ?: return@mapNotNull null
            if (parts.size != 2) return@mapNotNull null
            runCatching { Triple(period, LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim())) }.getOrNull()
        }.sortedBy { it.second }
        check(times.isNotEmpty()) { "学校暂未提供节次时间，请使用条件查询" }
        val time = now.toLocalTime()
        val next = times.firstOrNull { time < it.third }
            ?: error("今天的教学节次已结束，可用下方条件查询查看其他时段")
        return QuickLookupPlan(week, date.dayOfWeek.value, next.first, time < next.second)
    }

    fun isStudyBuilding(name: String): Boolean = listOf(
        "基础一", "基础二", "基础1", "基础2", "基础教学楼一", "基础教学楼二",
        "第一公共教学楼", "第二公共教学楼",
    ).any { name.contains(it) }
}
