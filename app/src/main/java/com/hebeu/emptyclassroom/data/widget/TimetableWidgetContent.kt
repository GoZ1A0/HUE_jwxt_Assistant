package com.hebeu.emptyclassroom.data.widget

import com.hebeu.emptyclassroom.data.reminder.CourseReminderPlanner
import com.hebeu.emptyclassroom.data.reminder.ReminderSnapshot
import com.hebeu.emptyclassroom.domain.model.ReminderSettings
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal data class WidgetEntry(val title: String, val detail: String, val phase: String)
internal data class WidgetContent(val date: String, val summary: String, val empty: String, val entries: List<WidgetEntry>,
    val title: String = "今日剩余课表", val nextRefreshAt: Long? = null)

/** Uses the accepted, active local timetable; never queries the school or a pending update. */
internal object TimetableWidgetContent {
    fun build(snapshot: ReminderSnapshot, now: Long = System.currentTimeMillis()): WidgetContent {
        val zone = CourseReminderPlanner.zone
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = "${today.monthValue}.${today.dayOfMonth}"
        val weekday = "今日周${"一二三四五六日"[today.dayOfWeek.value - 1]}"
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val schedule = snapshot.schedule
            ?: return WidgetContent(date, "HUE教务助手", "打开应用，同步或导入一份课表", emptyList(), nextRefreshAt = midnight)
        val plan = CourseReminderPlanner.plan(schedule, ReminderSettings(examEnabled = true), snapshot.lessonTimes, snapshot.exams)
        val todayCourses = plan.courses.filter { Instant.ofEpochMilli(it.startsAt).atZone(zone).toLocalDate() == today }
        val courses = todayCourses.filter { it.endsAt > now }.sortedBy { it.startsAt }
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val entries = courses.map { course ->
            val timing = "${time.format(Instant.ofEpochMilli(course.startsAt))}–${time.format(Instant.ofEpochMilli(course.endsAt))}"
            WidgetEntry((if (course.exam != null) "考试 · " else "") + course.lesson.name,
                listOfNotNull(timing, course.lesson.location?.takeIf { it.isNotBlank() },
                    course.exam?.seat?.let { "座位 $it" }, course.lesson.teacher?.takeIf { it.isNotBlank() }).joinToString(" · "),
                if (now >= course.startsAt) "进行中" else "待开始")
        }
        val weekNumber = schedule.startDate?.let { runCatching { ChronoUnit.DAYS.between(java.time.LocalDate.parse(it), today) }
            .getOrNull()?.takeIf { it >= 0 }?.let { days -> (days / 7 + 1).toInt() } }
        val week = weekNumber?.let { "第${it}周 · " }.orEmpty()
        val summary = week + (snapshot.displayName ?: schedule.term.label)
        val plannedIds = todayCourses.map { it.lesson.id }.toSet()
        val incomplete = plan.missingCalendar || schedule.lessons.any {
            it.scheduled && it.weekday == today.dayOfWeek.value && weekNumber in it.weeks && it.id !in plannedIds
        }
        val empty = if (incomplete) "请在应用中检查开学日期与课程作息时间" else "今天没课啦"
        val title = when {
            entries.isEmpty() && incomplete -> "今日剩余课表"
            entries.isEmpty() -> "$weekday，没课啦"
            courses.any { it.exam != null } -> "$weekday，还有${entries.size}项安排"
            else -> "$weekday，还有${entries.size}门课要上"
        }
        val next = (courses.flatMap { listOf(it.startsAt, it.endsAt) }.filter { it > now } + midnight).min()
        return WidgetContent(date, summary, empty, entries, title, next)
    }
}
