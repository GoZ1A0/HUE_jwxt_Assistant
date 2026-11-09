package com.hebeu.emptyclassroom.domain.model

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Serializable
data class SchoolNotice(val id: String, val title: String, val body: String, val createdAt: String) {
    val key: String get() = academicHash("$id|$title|$body|$createdAt")
}

@Serializable
data class ExamInfo(
    val id: String, val name: String, val courseCode: String?, val term: AcademicTerm,
    val timeText: String, val date: String? = null, val startTime: String? = null, val endTime: String? = null,
    val location: String? = null, val seat: String? = null, val type: String? = null, val note: String? = null,
) {
    fun startsAt(): Long? = runCatching { LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(startTime)).atZone(academicZone).toInstant().toEpochMilli() }.getOrNull()
    fun endsAt(): Long? = runCatching { LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(endTime)).atZone(academicZone).toInstant().toEpochMilli() }.getOrNull()
    val signature: String get() = listOf(courseCode, name, timeText, location, seat, type, note).joinToString("|")
}

@Serializable
data class AcademicRevision(val at: Long, val label: String, val schedule: ScheduleData?, val exams: List<ExamInfo>,
    val profiles: List<SavedTimetable> = emptyList(), val undoable: Boolean = true)

@Serializable
data class AcademicSyncState(
    val accepted: ScheduleData? = null,
    val exams: List<ExamInfo> = emptyList(),
    val latest: ScheduleData? = null,
    val latestExams: List<ExamInfo>? = null,
    val notices: List<SchoolNotice> = emptyList(),
    val handledNotices: Set<String> = emptySet(),
    val checkedAt: Long = 0,
    val history: List<AcademicRevision> = emptyList(),
) {
    val unread: List<SchoolNotice> get() = notices.filter { it.key !in handledNotices }
    val courseChanges: List<CourseChange> get() = AcademicChanges.diff(accepted, latest)
    val examChanged: Boolean get() = latestExams?.let { AcademicChanges.examSignature(it) != AcademicChanges.examSignature(exams) } == true
    val pending: Boolean get() = courseChanges.isNotEmpty() || AcademicChanges.calendarChanged(accepted, latest) || examChanged || unread.isNotEmpty()
    val batchKey: String get() = academicHash(AcademicChanges.scheduleSignature(latest) + AcademicChanges.examSignature(latestExams ?: exams) + unread.map { it.key }.sorted().joinToString())
}

data class CourseChange(val key: String, val name: String, val before: List<Lesson>, val after: List<Lesson>)
data class TimetableMerge(val schedule: ScheduleData, val conflicts: Set<String>)

/** 比较排课内容，不把响应顺序、同步时间或服务端行号变化误报为调课。 */
object AcademicChanges {
    private fun name(value: String) = value.filterNot(Char::isWhitespace).lowercase(java.util.Locale.ROOT)
    private fun keyer(all: List<Lesson>): (Lesson) -> String {
        val codes = all.groupBy { name(it.name) }.mapValues { (_, rows) -> rows.mapNotNull { it.courseCode }.distinct() }
        return { lesson -> lesson.courseCode?.let { "code:$it" }
            ?: codes[name(lesson.name)]?.singleOrNull()?.let { "code:$it" } ?: "name:${name(lesson.name)}" }
    }
    private fun signature(lesson: Lesson): String = listOf(lesson.name, lesson.teacher, lesson.location, lesson.weekday,
        lesson.periods.sorted(), lesson.weeks.sorted(), lesson.note, lesson.credit, lesson.nature).joinToString("|")
    private fun signatures(rows: List<Lesson>) = rows.map(::signature).sorted()
    fun scheduleSignature(data: ScheduleData?): String = data?.let {
        listOf(it.term.key, it.startDate, it.totalWeeks, it.periodTimes.toSortedMap(), signatures(it.lessons)).joinToString("|")
    }.orEmpty()
    fun examSignature(exams: List<ExamInfo>) = exams.map { it.signature }.sorted().joinToString("\n")
    fun diff(before: ScheduleData?, after: ScheduleData?): List<CourseChange> {
        if (before == null || after == null) return emptyList()
        val key = keyer(before.lessons + after.lessons)
        val old = before.lessons.groupBy(key); val new = after.lessons.groupBy(key)
        return (old.keys + new.keys).mapNotNull { id ->
            val a = old[id].orEmpty(); val b = new[id].orEmpty()
            // 新版本补充的学分等元数据不单独弹出调课确认。
            fun placements(rows: List<Lesson>) = signatures(rows.map { it.copy(credit = null, nature = null) })
            if (placements(a) == placements(b)) null else CourseChange(id, (b + a).first().name, a, b)
        }
    }
    fun calendarChanged(a: ScheduleData?, b: ScheduleData?) = a != null && b != null &&
        (a.startDate != b.startDate || a.totalWeeks != b.totalWeeks || a.periodTimes != b.periodTimes)
    fun merge(base: ScheduleData, local: ScheduleData, remote: ScheduleData, useSchool: Set<String> = emptySet()): TimetableMerge {
        val key = keyer(base.lessons + local.lessons + remote.lessons)
        val a = base.lessons.groupBy(key); val b = local.lessons.groupBy(key); val c = remote.lessons.groupBy(key)
        val conflicts = mutableSetOf<String>()
        val result = (a.keys + b.keys + c.keys).flatMap { id ->
            val old = a[id].orEmpty(); val mine = b[id].orEmpty(); val next = c[id].orEmpty()
            val edited = signatures(old) != signatures(mine)
            val moved = signatures(old) != signatures(next)
            if (edited && moved && signatures(mine) != signatures(next)) conflicts += id
            if (edited && id !in useSchool) mine else next
        }
        return TimetableMerge(local.copy(lessons = result, syncedAt = remote.syncedAt,
            startDate = if (local.startDate == base.startDate) remote.startDate else local.startDate,
            totalWeeks = if (local.totalWeeks == base.totalWeeks) remote.totalWeeks else local.totalWeeks,
            periodTimes = if (local.periodTimes == base.periodTimes) remote.periodTimes else local.periodTimes), conflicts)
    }
    fun gradesFor(lesson: Lesson, term: AcademicTerm, grades: List<GradeRecord>): List<GradeRecord> {
        val current = grades.filter { it.year == term.year && it.semester == term.semester }
        if (lesson.courseCode != null) return current.filter { it.courseCode == lesson.courseCode }
        val matching = current.filter { name(it.name) == name(lesson.name) }
        return matching.takeIf { it.mapNotNull { r -> r.courseCode }.distinct().size <= 1 }.orEmpty()
    }
    fun examsFor(lesson: Lesson, exams: List<ExamInfo>) = exams.filter {
        if (lesson.courseCode != null && it.courseCode != null) lesson.courseCode == it.courseCode else name(lesson.name) == name(it.name)
    }
}

val academicZone: ZoneId = ZoneId.of("Asia/Shanghai")
fun academicHash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
