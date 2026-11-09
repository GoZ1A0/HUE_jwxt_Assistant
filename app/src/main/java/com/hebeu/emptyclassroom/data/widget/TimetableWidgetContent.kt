package com.hebeu.emptyclassroom.data.widget

import com.hebeu.emptyclassroom.data.reminder.CourseReminderPlanner
import com.hebeu.emptyclassroom.data.reminder.ReminderSnapshot
import com.hebeu.emptyclassroom.domain.model.ReminderSettings
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal data class WidgetEntry(val title: String, val detail: String, val phase: String)
internal data class WidgetContent(val date: String, val summary: String, val empty: String, val entries: List<WidgetEntry>)

/** Uses the accepted, active local timetable; never queries the school or a pending update. */
internal object TimetableWidgetContent {
    fun build(snapshot: ReminderSnapshot, now: Long = System.currentTimeMillis()): WidgetContent {
        val zone = CourseReminderPlanner.zone
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = "${today.monthValue}月${today.dayOfMonth}日 周${"一二三四五六日"[today.dayOfWeek.value - 1]}"
        val schedule = snapshot.schedule
            ?: return WidgetContent(date, "HUE教务助手", "打开应用，同步或导入一份课表", emptyList())
        val plan = CourseReminderPlanner.plan(schedule, ReminderSettings(examEnabled = true), snapshot.lessonTimes, snapshot.exams)
        val courses = plan.courses.filter { Instant.ofEpochMilli(it.startsAt).atZone(zone).toLocalDate() == today }
            .sortedWith(compareBy({ it.endsAt <= now }, { it.startsAt }))
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val entries = courses.map { course ->
            val timing = "${time.format(Instant.ofEpochMilli(course.startsAt))}–${time.format(Instant.ofEpochMilli(course.endsAt))}"
            WidgetEntry((if (course.exam != null) "考试 · " else "") + course.lesson.name,
                listOfNotNull(timing, course.lesson.location?.takeIf { it.isNotBlank() },
                    course.exam?.seat?.let { "座位 $it" }, course.lesson.teacher?.takeIf { it.isNotBlank() }).joinToString(" · "),
                when { now >= course.endsAt -> "已结束"; now >= course.startsAt -> "进行中"; else -> "待开始" })
        }
        val week = schedule.startDate?.let { runCatching { ChronoUnit.DAYS.between(java.time.LocalDate.parse(it), today) }
            .getOrNull()?.takeIf { it >= 0 }?.let { days -> "第${days / 7 + 1}周 · " } }.orEmpty()
        val summary = week + (snapshot.displayName ?: schedule.term.label)
        val empty = if (plan.missingCalendar || plan.incompleteLessons > 0) "请在应用中检查开学日期与课程作息时间" else "今天没有课程或已确认考试"
        return WidgetContent(date, summary, empty, entries)
    }
}
