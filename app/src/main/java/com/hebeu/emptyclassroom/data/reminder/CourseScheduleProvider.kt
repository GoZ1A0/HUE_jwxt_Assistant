package com.hebeu.emptyclassroom.data.reminder

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import kotlinx.serialization.json.*

/**
 * 厂商课程卡片适配入口。导出查询仅向本应用和 APK 中已观察的系统卡片调用方开放；
 * 新 authority 是否被厂商桌面发现、接纳需对应设备验证。本入口不执行任何写操作。
 */
class CourseScheduleProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context ?: return response(2001, JsonArray(emptyList()))
        val packages = context.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (Binder.getCallingUid() != Process.myUid() && packages.none { it in trustedSystemCallers })
            return response(2001, JsonArray(emptyList()))
        if (uri.authority != "${context.packageName}.timetable.provider") return response(2004, JsonArray(emptyList()))
        val segments = uri.pathSegments
        val operation = segments.firstOrNull()
        val id = segments.getOrNull(1)
        if (segments.size > 2 || (id != null && id.toIntOrNull() != ACTIVE_TABLE_ID))
            return response(0, JsonArray(emptyList()))
        val saved = CourseReminderStore(context).load()
        val schedule = saved.schedule
        return when (operation) {
            "course_list", "next_course_list" -> response(0, CourseCardContract.courses(schedule,
                saved.lessonTimes, saved.courseColors, tomorrow = operation == "next_course_list"))
            "has_init" -> response(0, buildJsonObject { put("has_init", schedule != null) })
            "show_table_id" -> response(0, buildJsonObject { put("table_id", ACTIVE_TABLE_ID) })
            "table_list" -> response(0, buildJsonArray {
                if (schedule != null) add(buildJsonObject {
                    put("id", ACTIVE_TABLE_ID); put("tableName", saved.displayName ?: schedule.term.label)
                })
            })
            "table_config" -> response(0, buildJsonObject {
                put("id", ACTIVE_TABLE_ID)
                put("name", saved.displayName ?: schedule?.term?.label.orEmpty())
                put("startDate", schedule?.startDate.orEmpty())
                put("maxWeek", schedule?.totalWeeks ?: 0)
            })
            else -> response(2004, JsonArray(emptyList()))
        }
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.hebeu.timetable"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("课程卡片为只读入口")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("课程卡片为只读入口")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("课程卡片为只读入口")

    private fun response(code: Int, data: JsonElement): MatrixCursor = MatrixCursor(arrayOf("code", "data")).apply {
        addRow(arrayOf<Any>(code, data.toString()))
    }

    companion object {
        private const val ACTIVE_TABLE_ID = 1
        private val trustedSystemCallers = setOf("com.coloros.assistantscreen", "com.oplus.metis",
            "com.oplus.pantanal.ums", "com.vivo.aiengine", "com.hihonor.quickengine", "com.android.launcher")
    }
}
