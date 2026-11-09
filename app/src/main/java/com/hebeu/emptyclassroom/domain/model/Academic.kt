package com.hebeu.emptyclassroom.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AcademicTerm(val year: String, val semester: String, val label: String) {
    val key: String get() = "$year-$semester"
    fun asQueryTerm() = TermOption(key, year, semester, label)
}

@Serializable
data class AcademicOptions(val terms: List<AcademicTerm>, val selected: AcademicTerm)

@Serializable
data class Lesson(
    val id: String, val name: String, val teacher: String?, val location: String?,
    val weekday: Int?, val periods: List<Int>, val weeks: Set<Int>,
    val weekText: String, val periodText: String, val note: String? = null,
    val courseCode: String? = null, val credit: String? = null, val nature: String? = null,
) {
    val scheduled: Boolean get() = weekday in 1..7 && periods.isNotEmpty() && weeks.isNotEmpty()
}

@Serializable
data class ScheduleData(
    val term: AcademicTerm, val lessons: List<Lesson>, val syncedAt: Long,
    val startDate: String? = null, val totalWeeks: Int = 20,
    val periodTimes: Map<Int, String> = emptyMap(),
)

@Serializable
data class GradeRecord(
    val id: String, val name: String, val term: String, val score: String?,
    val credit: String?, val gradePoint: String?, val weightedPoint: String?,
    val nature: String?, val examType: String?,
    val courseCode: String? = null, val year: String? = null, val semester: String? = null,
)

@Serializable
data class GradeData(
    val records: List<GradeRecord>, val officialGpa: String?, val officialAverage: String?,
    val syncedAt: Long, val overviewAt: Long,
)

/** 正方课表中的文本周次、节次（不是空闲教室查询的位掩码）。 */
object SchedulePatterns {
    fun weeks(text: String): Set<Int> = text.replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',')
        .split(',').flatMap { segment ->
            val odd = segment.contains("单")
            val even = segment.contains("双")
            Regex("(\\d+)(?:\\s*[-－—~至]\\s*(\\d+))?").findAll(segment).flatMap { match ->
                val start = match.groupValues[1].toIntOrNull() ?: 0
                val end = match.groupValues[2].toIntOrNull() ?: start
                (start..end.coerceAtMost(60)).asSequence().filter { it in 1..60 && (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
            }.toList()
        }.toSet()

    fun periods(text: String): List<Int> = weeks(text).filter { it in 1..30 }.sorted()
}
