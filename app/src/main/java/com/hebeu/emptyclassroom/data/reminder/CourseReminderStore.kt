package com.hebeu.emptyclassroom.data.reminder

import android.content.Context
import android.util.AtomicFile
import com.hebeu.emptyclassroom.domain.model.ReminderSettings
import com.hebeu.emptyclassroom.domain.model.ScheduleData
import com.hebeu.emptyclassroom.domain.model.ImportedLessonTime
import com.hebeu.emptyclassroom.domain.model.ExamInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class ReminderSnapshot(
    val schedule: ScheduleData? = null,
    val settings: ReminderSettings = ReminderSettings(),
    val nextAlarmAt: Long? = null,
    val delivered: Set<String> = emptySet(),
    val lessonTimes: Map<String, ImportedLessonTime> = emptyMap(),
    val unpinnedLiveCourses: Set<String> = emptySet(),
    val courseColors: Map<String, String> = emptyMap(),
    val displayName: String? = null,
    val exams: List<ExamInfo> = emptyList(),
)

/** 闹钟接收器可独立读取的课表副本，存于 noBackupFilesDir，永不包含登录凭据。 */
internal class CourseReminderStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "course-reminders.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun load(): ReminderSnapshot = synchronized(lock) { runCatching {
        json.decodeFromString<ReminderSnapshot>(file.readFully().toString(Charsets.UTF_8))
    }.getOrDefault(ReminderSnapshot()) }
    fun save(snapshot: ReminderSnapshot) = synchronized(lock) {
        val bytes = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun clear() = synchronized(lock) { file.delete() }
    companion object { val lock = Any() }
}
