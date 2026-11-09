package com.hebeu.emptyclassroom.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TimetableAppearance(
    val palette: String = "pastel",
    val rowHeightDp: Int = 76,
    val textSizeSp: Int = 12,
    val cornerRadiusDp: Int = 12,
    val cardAlpha: Float = 0.95f,
    val showTimes: Boolean = true,
    val showTeacher: Boolean = true,
    val showLocation: Boolean = true,
    val showSaturday: Boolean = true,
    val showSunday: Boolean = true,
    val showOtherWeeks: Boolean = false,
    val sundayFirst: Boolean = false,
    val nodes: Int = 10,
    val wallpaperPath: String? = null,
    val textColorArgb: Long? = null,
    val courseTextColorArgb: Long? = null,
    val centeredText: Boolean = false,
    val boldText: Boolean = false,
)

@Serializable
data class SavedTimetable(
    val id: String,
    val name: String,
    val schedule: ScheduleData,
    val appearance: TimetableAppearance = TimetableAppearance(),
    val colors: Map<String, String> = emptyMap(),
    val lessonTimes: Map<String, ImportedLessonTime> = emptyMap(),
    val credits: Map<String, String> = emptyMap(),
    val schoolBase: ScheduleData? = null,
)

@Serializable
data class TimetableWorkspace(
    val activeId: String = "school",
    val profiles: List<SavedTimetable> = emptyList(),
    val schoolAppearance: TimetableAppearance = TimetableAppearance(),
    val schoolColors: Map<String, String> = emptyMap(),
    val reminders: ReminderSettings = ReminderSettings(),
    val academic: Map<String, AcademicSyncState> = emptyMap(),
    val schoolTermKey: String? = null,
    val deferredUpdates: Set<String> = emptySet(),
)

@Serializable
data class TimetableExport(val format: String = "hebeu-timetable-v1", val timetable: SavedTimetable)
