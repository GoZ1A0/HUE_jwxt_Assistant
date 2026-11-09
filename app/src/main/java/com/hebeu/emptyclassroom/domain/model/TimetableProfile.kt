package com.hebeu.emptyclassroom.domain.model

import kotlinx.serialization.Serializable

/** WakeUp 备份的应用配置单独存放，不混入教务接口模型。颜色键都是转换后的 Lesson.id。 */
@Serializable
data class ImportedTimetable(
    val name: String,
    val schedule: ScheduleData,
    val courseColors: Map<String, String> = emptyMap(),
    val importedStyle: ImportedTimetableStyle = ImportedTimetableStyle(),
    val lessonTimes: Map<String, ImportedLessonTime> = emptyMap(),
    val courseCredits: Map<String, String> = emptyMap(),
    val sourceFormat: String = "wakeup-5-line-json",
)

@Serializable
data class ImportedLessonTime(val start: String, val end: String)

@Serializable
data class ImportedTimetableStyle(
    val backgroundReference: String? = null,
    val textColorArgb: Long = 0xFF101820,
    val courseTextColorArgb: Long = 0xFF101820,
    val strokeColorArgb: Long = 0,
    val cardAlpha: Float = 1f,
    val rowHeightDp: Int = 72,
    val textSizeSp: Int = 12,
    val nodes: Int = 12,
    val showSaturday: Boolean = true,
    val showSunday: Boolean = true,
    val showTimes: Boolean = true,
    val showOtherWeeks: Boolean = false,
    val sundayFirst: Boolean = false,
)
