package com.hebeu.emptyclassroom.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 中文格式化工具（纯函数，方便单测）。
 *
 * 只做展示格式化，不做任何业务推断；日期换算仅按教务校历公式：
 * `日期 = 学期开始日 + (周-1)*7 + (星期-1)`（ui-spec / site-contract §4.4 已验证）。
 */
object Formatters {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
    private val ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val PERIOD_PART_ORDER = listOf("上午", "下午", "晚上")

    /** 1=周一 … 7=周日。越界返回原数字文本，避免抛异常。 */
    fun weekdayName(weekday: Int): String =
        WEEKDAY_NAMES.getOrNull(weekday - 1) ?: "周$weekday"

    fun todayWeekday(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value

    /** 例：[1,3,5] → "周一、周三、周五" */
    fun formatWeekdays(weekdays: Collection<Int>): String =
        weekdays.filter { it in 1..7 }.distinct().sorted().joinToString("、") { weekdayName(it) }

    /** 例：[1,2,3,5] → "第1-3、5周" */
    fun formatWeeksCompact(weeks: Collection<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return "未选周次"
        return "第" + compressRanges(sorted) + "周"
    }

    /** 例：[1,2,5] → "第1-2、5节" */
    fun formatPeriodsCompact(periods: Collection<Int>): String {
        val sorted = periods.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return "未选节次"
        return "第" + compressRanges(sorted) + "节"
    }

    /** 把连续整数压缩成 "1-3、5" 形式。 */
    internal fun compressRanges(sortedDistinct: List<Int>): String {
        if (sortedDistinct.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var rangeStart = sortedDistinct.first()
        var previous = rangeStart
        for (value in sortedDistinct.drop(1)) {
            if (value == previous + 1) {
                previous = value
                continue
            }
            parts += if (rangeStart == previous) "$rangeStart" else "$rangeStart-$previous"
            rangeStart = value
            previous = value
        }
        parts += if (rangeStart == previous) "$rangeStart" else "$rangeStart-$previous"
        return parts.joinToString("、")
    }

    /** 本地时间 "HH:mm"（查询时间用）。 */
    fun formatTimeOfDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** "11月2日" */
    fun formatMonthDay(date: LocalDate): String = MONTH_DAY_FORMATTER.format(date)

    /** "2026-11-02" */
    fun formatIsoDate(date: LocalDate): String = ISO_DATE_FORMATTER.format(date)

    /** "11-02 17:21"（最近查询列表等紧凑场景） */
    fun formatShortDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATETIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /**
     * 按校历推算「第 week 周 周 weekday」的日期（site-contract §4.4 已验证公式）。
     * 学期开始日未知时返回 null，调用方不得自行猜测。
     */
    fun inferDate(termStartDate: LocalDate?, week: Int, weekday: Int): LocalDate? {
        if (termStartDate == null) return null
        if (week < 1 || weekday !in 1..7) return null
        return termStartDate.plusDays((week - 1L) * 7L + (weekday - 1L))
    }

    /** 第 week 周的起止日（周一 ~ 周日）；学期开始日未知时返回 null。 */
    fun weekDateRange(termStartDate: LocalDate?, week: Int): Pair<LocalDate, LocalDate>? {
        val start = inferDate(termStartDate, week, 1) ?: return null
        return start to start.plusDays(6)
    }

    /**
     * 按校历推算今天是第几教学周（四舍五入到最近一周，并 clamp 到 1..totalWeeks）。
     * 学期开始日或总周数未知时返回 null；不使用服务端 DQZC（与实际日期不符，契约已确认）。
     */
    fun currentTeachingWeek(
        termStartDate: LocalDate?,
        totalWeeks: Int?,
        today: LocalDate = LocalDate.now(),
    ): Int? {
        if (termStartDate == null || totalWeeks == null || totalWeeks <= 0) return null
        val rawDays = ChronoUnit.DAYS.between(termStartDate, today)
        val week = Math.floorDiv(rawDays, 7L).toInt() + 1
        return week.coerceIn(1, totalWeeks)
    }

    /** 周次区间的展示文本："11/02-11/09"（真实 rqfw）；缺失时用校历推算回退。 */
    fun weekRangeText(
        week: Int,
        dateRangeText: String?,
        termStartDate: LocalDate?,
    ): String? {
        if (!dateRangeText.isNullOrBlank()) return dateRangeText
        val (start, end) = weekDateRange(termStartDate, week) ?: return null
        return "${MONTH_DAY_FORMATTER.format(start)}-${MONTH_DAY_FORMATTER.format(end)}"
    }

    /** 节次分组排序：上午 → 下午 → 晚上 → 其他。 */
    fun sortedPartOfDay(parts: Collection<String>): List<String> =
        parts.distinct().sortedWith(compareBy { part ->
            val index = PERIOD_PART_ORDER.indexOf(part)
            if (index >= 0) index else Int.MAX_VALUE
        })

    /** 大字体/横屏时不溢出：把过长文本截断为 n 个字符 + 省略号（仅用于非关键文本）。 */
    fun ellipsize(text: String?, maxLength: Int): String? {
        if (text == null) return null
        if (text.length <= maxLength) return text
        return text.take((maxLength - 1).coerceAtLeast(1)) + "…"
    }

    /** 座位数等可空整数的展示。 */
    fun formatCount(value: Int?): String? = value?.takeIf { it >= 0 }?.toString()

    /** 数字字符串安全转 Int（座输入等）。 */
    fun parseIntOrNull(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

    /** 判断两个可空字符串是否“等价选择”（用于联动重置）。 */
    fun sameSelection(a: String?, b: String?): Boolean = (a ?: "") == (b ?: "")

    /** 距离参考日期 abs 天数的辅助（单测/展示用）。 */
    internal fun daysBetween(from: LocalDate, to: LocalDate): Long = abs(ChronoUnit.DAYS.between(from, to))
}
