package com.hebeu.emptyclassroom.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.hebeu.emptyclassroom.MainActivity
import com.hebeu.emptyclassroom.R
import com.hebeu.emptyclassroom.data.reminder.CourseReminderStore
import java.util.concurrent.Executors

class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = updateAsync(context)
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
            if (ids.isEmpty()) return
            val content = TimetableWidgetContent.build(CourseReminderStore(context).load())
            ids.forEach { id -> manager.updateAppWidget(id, views(context, content, manager.getAppWidgetOptions(id))) }
        }

        internal fun views(context: Context, content: WidgetContent, options: Bundle): RemoteViews {
            val open = PendingIntent.getActivity(context, 311,
                Intent(context, MainActivity::class.java).putExtra("open_schedule", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val refresh = PendingIntent.getBroadcast(context, 312,
                Intent(context, TimetableWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
            val count = ((minHeight - 140) / 62).coerceIn(1, 6)
            return RemoteViews(context.packageName, R.layout.timetable_widget).apply {
                setTextViewText(R.id.widget_date, content.date)
                setTextViewText(R.id.widget_summary, content.summary)
                setViewVisibility(R.id.widget_empty, if (content.entries.isEmpty()) View.VISIBLE else View.GONE)
                setTextViewText(R.id.widget_empty, content.empty)
                removeAllViews(R.id.widget_rows)
                content.entries.take(count).forEach { entry ->
                    addView(R.id.widget_rows, RemoteViews(context.packageName, R.layout.timetable_widget_row).apply {
                        setTextViewText(R.id.widget_course, entry.title)
                        setTextViewText(R.id.widget_detail, entry.detail)
                        setTextViewText(R.id.widget_phase, entry.phase)
                    })
                }
                val rest = content.entries.size - count
                setTextViewText(R.id.widget_hint, if (rest > 0) "还有 $rest 项 · 点击查看全部" else "点击打开课表")
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_refresh, refresh)
            }
        }
    }
}
