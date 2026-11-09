package com.hebeu.emptyclassroom.data.widget

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.hebeu.emptyclassroom.MainActivity
import com.hebeu.emptyclassroom.R
import com.hebeu.emptyclassroom.data.reminder.CourseReminderStore
import java.util.concurrent.Executors

class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = updateAsync(context)
    override fun onDisabled(context: Context) { cancelRefresh(context) }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(ACTION_REFRESH, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) updateAsync(context)
        else super.onReceive(context, intent)
    }

    private fun updateAsync(context: Context) {
        val pending = goAsync()
        executor.execute { try { refreshAll(context) } finally { pending?.finish() } }
    }

    companion object {
        const val ACTION_REFRESH = "com.hebeu.emptyclassroom.widget.REFRESH"
        private val executor = Executors.newSingleThreadExecutor()

        internal fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimetableWidgetProvider::class.java))
            if (ids.isEmpty()) { cancelRefresh(context); return }
            val content = TimetableWidgetContent.build(CourseReminderStore(context).load())
            ids.forEach { id -> manager.updateAppWidget(id, views(context, content, manager.getAppWidgetOptions(id))) }
            content.nextRefreshAt?.let { at ->
                val alarms = context.getSystemService(AlarmManager::class.java)
                val pending = refreshIntent(context)
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                    try { alarms.setExact(AlarmManager.RTC, at, pending); return }
                    catch (_: SecurityException) { /* Special access may have changed since the check. */ }
                }
                // No permission prompt and no wake-up for a widget-only update.
                alarms.set(AlarmManager.RTC, at, pending)
            }
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 312,
            Intent(context, TimetableWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun cancelRefresh(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
        }

        internal fun views(context: Context, content: WidgetContent, options: Bundle): RemoteViews {
            val open = PendingIntent.getActivity(context, 311,
                Intent(context, MainActivity::class.java).putExtra("open_schedule", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 380)
            val columns = if (width >= 250) 2 else 1
            val scale = (context.resources.configuration.fontScale - 1f).coerceAtLeast(0f)
            val rows = ((minHeight - 104 - 30 * scale) / (58 + 28 * scale)).toInt().coerceIn(1, 4)
            val count = rows * columns
            val visible = content.entries.take(count)
            val hidden = content.entries.size - visible.size
            return RemoteViews(context.packageName, R.layout.timetable_widget).apply {
                val heading = if (width >= 320) content.title else when {
                    content.entries.isNotEmpty() -> "还剩${content.entries.size}项安排"
                    content.title.contains("没课啦") -> "今天没课啦"
                    else -> content.title
                }
                val title = SpannableString(heading)
                Regex("\\d+").find(heading)?.range?.let { range ->
                    title.setSpan(ForegroundColorSpan(context.getColor(R.color.widget_count)), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                setTextViewText(R.id.widget_heading, title)
                setTextViewText(R.id.widget_date, "${content.date} / ${content.summary}")
                setViewVisibility(R.id.widget_empty, if (content.entries.isEmpty()) View.VISIBLE else View.GONE)
                setTextViewText(R.id.widget_empty, content.empty)
                removeAllViews(R.id.widget_rows)
                val backgrounds = listOf(R.drawable.widget_card_blue, R.drawable.widget_card_pink,
                    R.drawable.widget_card_purple, R.drawable.widget_card_amber)
                visible.chunked(columns).forEachIndexed { rowIndex, entries ->
                    val row = RemoteViews(context.packageName, R.layout.timetable_widget_grid_row)
                    entries.forEachIndexed { column, entry ->
                        val last = rowIndex * columns + column == visible.lastIndex
                        row.addView(R.id.widget_grid_row, RemoteViews(context.packageName, R.layout.timetable_widget_row).apply {
                            setInt(R.id.widget_card, "setBackgroundResource", backgrounds[Math.floorMod(entry.title.hashCode(), backgrounds.size)])
                            setTextViewText(R.id.widget_course, entry.title)
                            setTextViewText(R.id.widget_detail, when {
                                last && hidden > 0 -> "等剩余 $hidden 项安排 ›"
                                entry.phase == "进行中" -> "进行中 · ${entry.detail}"
                                else -> entry.detail
                            })
                        })
                    }
                    addView(R.id.widget_rows, row)
                }
                setTextViewText(R.id.widget_hint, if (hidden > 0) "还有 $hidden 项 · 点击查看全部" else "点击打开课表")
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
            }
        }
    }
}
