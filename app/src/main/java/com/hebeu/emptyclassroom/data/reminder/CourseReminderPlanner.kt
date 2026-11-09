package com.hebeu.emptyclassroom.data.reminder

import com.hebeu.emptyclassroom.domain.model.Lesson
import com.hebeu.emptyclassroom.domain.model.ReminderSettings
import com.hebeu.emptyclassroom.domain.model.ScheduleData
import com.hebeu.emptyclassroom.domain.model.ImportedLessonTime
import com.hebeu.emptyclassroom.domain.model.ExamInfo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CourseOccurrence(
    val key: String,
    val lesson: Lesson,
    val week: Int,
    val startsAt: Long,
    val endsAt: Long,
    val periods: List<Int>,
    val exam: ExamInfo? = null,
)

enum class CourseReminderEventKind { BEFORE_CLASS, BEFORE_END, LIVE_START, LIVE_END, BEFORE_EXAM, EXAM_DAY_BEFORE }

data class CourseReminderEvent(val at: Long, val course: CourseOccurrence, val kind: CourseReminderEventKind) {
    val key: String get() = "${course.key}:${kind.name}"
}

data class CourseReminderPlan(
    val courses: List<CourseOccurrence>,
    val events: List<CourseReminderEvent>,
    val missingCalendar: Boolean = false,
    val incompleteLessons: Int = 0,
)

/** 纯函数排程：使用明确周次与北京时间，把非连续小节拆成独立上课片段。 */
object CourseReminderPlanner {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private data class PeriodTime(val number: Int, val start: LocalTime, val end: LocalTime)

    fun plan(schedule: ScheduleData?, preferences: ReminderSettings,
        lessonTimes: Map<String, ImportedLessonTime> = emptyMap(), exams: List<ExamInfo> = emptyList()): CourseReminderPlan {
        val classes = classPlan(schedule, preferences, lessonTimes)
        val settings = preferences.normalized()
        if (!settings.examEnabled) return classes
        val occurrences = exams.mapNotNull { exam ->
            val start = exam.startsAt() ?: return@mapNotNull null
            val end = exam.endsAt()?.takeIf { it > start } ?: return@mapNotNull null
            CourseOccurrence("exam:${exam.term.key}:${exam.id}:$start:$end", Lesson("exam:${exam.id}", exam.name, null,
                exam.location, null, emptyList(), emptySet(), "", "", courseCode = exam.courseCode), 0, start, end, emptyList(), exam)
        }
        val events = occurrences.flatMap { course -> buildList {
            add(CourseReminderEvent(course.startsAt - settings.examMinutesBefore * 60_000L, course, CourseReminderEventKind.BEFORE_EXAM))
            if (settings.examDayBefore) add(CourseReminderEvent(java.time.Instant.ofEpochMilli(course.startsAt).atZone(zone)
                .toLocalDate().minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli(), course, CourseReminderEventKind.EXAM_DAY_BEFORE))
            if (settings.ongoingEnabled) {
                add(CourseReminderEvent(course.startsAt, course, CourseReminderEventKind.LIVE_START))
                add(CourseReminderEvent(course.endsAt, course, CourseReminderEventKind.LIVE_END))
            }
        } }
        return classes.copy(courses = (classes.courses + occurrences).sortedBy { it.startsAt },
            events = (classes.events + events).sortedBy { it.at }, missingCalendar = classes.missingCalendar && occurrences.isEmpty())
    }

    private fun classPlan(schedule: ScheduleData?, preferences: ReminderSettings,
        lessonTimes: Map<String, ImportedLessonTime> = emptyMap()): CourseReminderPlan {
        val settings = preferences.normalized()
        if (schedule == null) return CourseReminderPlan(emptyList(), emptyList())
        val start = schedule.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return CourseReminderPlan(emptyList(), emptyList(), missingCalendar = true)
        val times = schedule.periodTimes.mapNotNull { (period, text) ->
            val parts = text.replace('－', '-').replace('—', '-').replace('–', '-').split('-')
            if (parts.size != 2) return@mapNotNull null
            runCatching {
                PeriodTime(period, LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim()))
                    .takeIf { it.end > it.start }
            }.getOrNull()
        }.associateBy { it.number }
        var incomplete = 0
        val occurrences = schedule.lessons.flatMap { lesson ->
            val numbers = lesson.periods.distinct().sorted()
            val custom = lessonTimes[lesson.id]
            val customRange = custom?.let { time -> runCatching {
                LocalTime.parse(time.start.trim()) to LocalTime.parse(time.end.trim())
            }.getOrNull()?.takeIf { it.second > it.first } }
            if (!lesson.scheduled || (custom != null && customRange == null) || (custom == null && numbers.any { it !in times })) {
                incomplete++
                return@flatMap emptyList()
            }
            val blocks = mutableListOf<MutableList<Int>>()
            if (customRange != null) blocks.add(numbers.toMutableList())
            else numbers.forEach { number ->
                val previous = blocks.lastOrNull()?.lastOrNull()
                val sameBlock = previous != null && number == previous + 1 &&
                    ChronoUnit.MINUTES.between(times.getValue(previous).end, times.getValue(number).start) in 0..30
                if (sameBlock) blocks.last().add(number) else blocks.add(mutableListOf(number))
            }
            lesson.weeks.filter { it in 1..60 }.sorted().flatMap { week ->
                val day = start.plusWeeks((week - 1).toLong()).plusDays((lesson.weekday!! - 1).toLong())
                blocks.map { periods ->
                    val beginning = day.atTime(customRange?.first ?: times.getValue(periods.first()).start).atZone(zone).toInstant().toEpochMilli()
                    val ending = day.atTime(customRange?.second ?: times.getValue(periods.last()).end).atZone(zone).toInstant().toEpochMilli()
                    CourseOccurrence("${schedule.term.key}:${lesson.id}:$day:${periods.first()}", lesson, week, beginning, ending, periods)
                }
            }
        }.sortedWith(compareBy({ it.startsAt }, { it.key }))
        val events = occurrences.flatMap { course -> buildList {
            if (settings.enabled) add(CourseReminderEvent(course.startsAt - settings.minutesBefore * 60_000L, course, CourseReminderEventKind.BEFORE_CLASS))
            if (settings.endEnabled) add(CourseReminderEvent((course.endsAt - settings.minutesBeforeEnd * 60_000L).coerceAtLeast(course.startsAt), course, CourseReminderEventKind.BEFORE_END))
            if (settings.ongoingEnabled) {
                add(CourseReminderEvent(course.startsAt - settings.minutesBefore * 60_000L, course, CourseReminderEventKind.LIVE_START))
                add(CourseReminderEvent(course.startsAt, course, CourseReminderEventKind.LIVE_START))
                add(CourseReminderEvent(course.endsAt, course, CourseReminderEventKind.LIVE_END))
            }
        } }.distinctBy { "${it.key}:${it.at}" }.sortedWith(compareBy({ it.at }, { it.key }))
        return CourseReminderPlan(occurrences, events, incompleteLessons = incomplete)
    }

    fun liveCourses(plan: CourseReminderPlan, now: Long, preferences: ReminderSettings): List<CourseOccurrence> {
        if (!preferences.ongoingEnabled) return emptyList()
        val active = plan.courses.filter { now >= it.startsAt && now < it.endsAt }
        if (active.isNotEmpty()) return active
        return plan.courses.filter { now >= it.startsAt - preferences.normalized().minutesBefore * 60_000L && now < it.startsAt }
    }
}
