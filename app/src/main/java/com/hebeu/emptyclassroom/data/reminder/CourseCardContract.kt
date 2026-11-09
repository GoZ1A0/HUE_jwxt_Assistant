package com.hebeu.emptyclassroom.data.reminder

import com.hebeu.emptyclassroom.domain.model.ImportedLessonTime
import com.hebeu.emptyclassroom.domain.model.ReminderSettings
import com.hebeu.emptyclassroom.domain.model.ScheduleData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeFormatter

/** 与已观察 WakeUp 只读课程卡片的数据字段兼容；不复用原应用 authority 或厂商身份。 */
object CourseCardContract {
    fun courses(schedule: ScheduleData?, lessonTimes: Map<String, ImportedLessonTime> = emptyMap(),
        courseColors: Map<String, String> = emptyMap(), tomorrow: Boolean = false,
        now: Long = System.currentTimeMillis()): JsonArray {
        val today = Instant.ofEpochMilli(now).atZone(CourseReminderPlanner.zone).toLocalDate()
        val date = if (tomorrow) today.plusDays(1) else today
        val occurrences = CourseReminderPlanner.plan(schedule, ReminderSettings(), lessonTimes).courses.filter { course ->
            Instant.ofEpochMilli(course.startsAt).atZone(CourseReminderPlanner.zone).toLocalDate() == date &&
                (tomorrow || course.endsAt > now)
        }
        val format = DateTimeFormatter.ofPattern("HH:mm").withZone(CourseReminderPlanner.zone)
        return buildJsonArray {
            occurrences.forEach { course -> add(buildJsonObject {
                put("id", course.key.hashCode() and Int.MAX_VALUE)
                put("courseName", course.lesson.name)
                put("room", course.lesson.location.orEmpty())
                put("teacher", course.lesson.teacher.orEmpty())
                put("startTime", format.format(Instant.ofEpochMilli(course.startsAt)))
                put("endTime", format.format(Instant.ofEpochMilli(course.endsAt)))
                put("color", courseColors[course.lesson.id] ?: "#90CAF9")
                put("extra", course.lesson.note.orEmpty())
                put("startTimestamp", course.startsAt / 1000)
                put("endTimestamp", course.endsAt / 1000)
            }) }
        }
    }
}
