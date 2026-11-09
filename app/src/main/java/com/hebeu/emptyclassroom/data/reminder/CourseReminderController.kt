package com.hebeu.emptyclassroom.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hebeu.emptyclassroom.MainActivity
import com.hebeu.emptyclassroom.R
import com.hebeu.emptyclassroom.domain.model.ReminderSettings
import com.hebeu.emptyclassroom.domain.model.ScheduleData
import com.hebeu.emptyclassroom.domain.model.ImportedLessonTime
import com.hebeu.emptyclassroom.domain.model.ExamInfo
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ReminderStatus(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val promotedAllowed: Boolean?,
    val scheduledEvents: Int,
    val message: String,
    val nextTriggerAt: Long? = null,
    val validCourses: Int = 0,
)

/**
 * 仅保留一个最近事件闹钟，每次触发后安排下一事件；无需常驻服务或网络。
 * 开启/课表变更调用 update，回到前台或获得权限调用 reschedule，退出账号调用 cancel。
 */
class CourseReminderController(context: Context) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val store = CourseReminderStore(this.context)

    fun settings(): ReminderSettings = synchronized(lock) { store.load().settings }

    fun update(schedule: ScheduleData?, settings: ReminderSettings,
        lessonTimes: Map<String, ImportedLessonTime> = emptyMap(),
        courseColors: Map<String, String> = emptyMap(), displayName: String? = null,
        exams: List<ExamInfo> = emptyList()): ReminderStatus = synchronized(lock) {
        cancelAlarm()
        clearOwnedNotifications()
        store.save(ReminderSnapshot(schedule, settings.normalized(), lessonTimes = lessonTimes,
            courseColors = courseColors, displayName = displayName, exams = exams, delivered = store.load().delivered))
        refresh(processDue = false)
    }

    fun reschedule(): ReminderStatus = synchronized(lock) { refresh(processDue = true) }

    fun status(): ReminderStatus = synchronized(lock) {
        val saved = store.load()
        val plan = CourseReminderPlanner.plan(saved.schedule, saved.settings, saved.lessonTimes, saved.exams)
        status(saved, plan, System.currentTimeMillis())
    }

    fun cancel(clearSavedSchedule: Boolean = true) = synchronized(lock) {
        cancelAlarm()
        clearOwnedNotifications()
        if (clearSavedSchedule) store.clear()
        else store.save(store.load().copy(settings = ReminderSettings(), nextAlarmAt = null, delivered = emptySet()))
    }

    internal fun handleAlarm() = synchronized(lock) { refresh(processDue = true); Unit }

    internal fun unpin() = synchronized(lock) {
        val saved = store.load()
        val plan = CourseReminderPlanner.plan(saved.schedule, saved.settings, saved.lessonTimes, saved.exams)
        val current = CourseReminderPlanner.liveCourses(plan, System.currentTimeMillis(), saved.settings)
        store.save(saved.copy(unpinnedLiveCourses = (saved.unpinnedLiveCourses + current.map { it.key }).toList().takeLast(512).toSet()))
        notifications.cancel(LIVE_TAG, LIVE_NOTIFICATION)
        refresh(processDue = true)
        Unit
    }

    private fun refresh(processDue: Boolean): ReminderStatus {
        createChannels()
        val now = System.currentTimeMillis()
        var saved = store.load()
        val plan = CourseReminderPlanner.plan(saved.schedule, saved.settings, saved.lessonTimes, saved.exams)
        val permission = notificationsAllowed()
        if (!saved.settings.active || !permission || (saved.schedule == null && saved.exams.isEmpty()) || plan.missingCalendar) {
            cancelAlarm()
            notifications.cancel(LIVE_TAG, LIVE_NOTIFICATION)
            saved = saved.copy(nextAlarmAt = null)
            store.save(saved)
            return status(saved, plan, now)
        }
        val delivered = saved.delivered.toMutableSet()
        if (processDue) saved.nextAlarmAt?.let { expected ->
            plan.events.filter { event -> event.at >= expected && event.at <= now && event.key !in delivered }
                .filter { event -> event.kind in listOf(CourseReminderEventKind.BEFORE_CLASS, CourseReminderEventKind.BEFORE_END, CourseReminderEventKind.BEFORE_EXAM, CourseReminderEventKind.EXAM_DAY_BEFORE) }
                .filter { event -> now <= event.course.endsAt + if (event.kind == CourseReminderEventKind.BEFORE_END) 5 * 60_000L else 0L }
                .filter { event -> when (event.kind) {
                    CourseReminderEventKind.BEFORE_CLASS, CourseReminderEventKind.BEFORE_EXAM -> now <= event.course.startsAt + 60_000L
                    CourseReminderEventKind.EXAM_DAY_BEFORE -> now <= event.at + 60 * 60_000L
                    else -> now <= event.course.endsAt + 5 * 60_000L
                } }
                .forEach { event ->
                    postReminder(event, now)
                    delivered += event.key
                }
        }
        showLive(CourseReminderPlanner.liveCourses(plan, now, saved.settings).filter { it.key !in saved.unpinnedLiveCourses }, now)
        val next = plan.events.firstOrNull { it.at > now }?.at
        cancelAlarm()
        if (next != null) scheduleAlarm(next, saved.settings.exactTiming)
        saved = saved.copy(nextAlarmAt = next, delivered = delivered.toList().takeLast(512).toSet())
        store.save(saved)
        return status(saved, plan, now)
    }

    private fun status(saved: ReminderSnapshot, plan: CourseReminderPlan, now: Long): ReminderStatus {
        val allowed = notificationsAllowed()
        val exact = exactAlarmsAllowed()
        val future = plan.events.count { it.at > now }
        val message = when {
            !saved.settings.active -> "课程提醒和课程实况均已关闭"
            saved.schedule == null && saved.exams.isEmpty() -> "请选择并保存一份课表"
            plan.missingCalendar -> "请先设置课表的开学日期"
            !allowed -> "请开启通知权限后使用课程提醒"
            plan.courses.isEmpty() -> "课表没有可安排提醒的课程，请检查周次和节次时间"
            future == 0 -> "本学期已无后续课程提醒"
            saved.settings.exactTiming && !exact -> "已安排课程提醒；开启“闹钟和提醒”权限可按时触发"
            else -> "已安排课程提醒，按北京时间和实际教学周执行"
        }
        val promoted = if (Build.VERSION.SDK_INT >= 36) notifications.canPostPromotedNotifications() else null
        return ReminderStatus(allowed, exact, promoted, future, message, saved.nextAlarmAt, plan.courses.size)
    }

    private fun notificationsAllowed(): Boolean = notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun exactAlarmsAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    private fun pendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(context, ALARM_REQUEST,
        Intent(context, CourseReminderReceiver::class.java).setAction(ACTION_TICK), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelAlarm() { pendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() } }

    private fun scheduleAlarm(at: Long, exact: Boolean) {
        val pending = requireNotNull(pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT))
        if (exact && exactAlarmsAllowed()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return }
            catch (_: SecurityException) { /* 特殊访问权限可能刚刚被撤销，保留普通排程。 */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(context, OPEN_REQUEST,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_SCHEDULE, true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun createChannels() {
        notifications.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "课程与考试提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "根据课表和提前时间发送课程提醒"
        })
        notifications.createNotificationChannel(NotificationChannel(LIVE_CHANNEL, "课程实况", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示正在上课或即将开始的课程及倒计时"
        })
    }

    private fun postReminder(event: CourseReminderEvent, now: Long) {
        val course = event.course
        val end = event.kind == CourseReminderEventKind.BEFORE_END
        val phase = if (end) { if (now >= course.endsAt) "已下课" else "即将下课" }
            else { if (now >= course.startsAt) "开始上课" else "即将上课" }
        val title = "${course.lesson.name} · " + if (course.exam == null) phase else
            if (event.kind == CourseReminderEventKind.EXAM_DAY_BEFORE) "明天考试" else "即将考试"
        val text = listOfNotNull(course.exam?.date, course.lesson.location, "${time(course.startsAt)}–${time(course.endsAt)}",
            course.exam?.seat?.let { "座位 $it" }, course.lesson.teacher).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_EVENT).setAutoCancel(true).setOnlyAlertOnce(true)
            .setWhen(if (end) course.endsAt else course.startsAt).setShowWhen(true)
            .setTimeoutAfter((course.endsAt + 5 * 60_000L - now).coerceAtLeast(1L)).build()
        try { notifications.notify(REMINDER_TAG, event.key.hashCode(), notification) }
        catch (_: SecurityException) { /* 用户可在闹钟触发与通知投递之间撤销权限。 */ }
    }

    private fun showLive(courses: List<CourseOccurrence>, now: Long) {
        if (courses.isEmpty()) { notifications.cancel(LIVE_TAG, LIVE_NOTIFICATION); return }
        val first = courses.first()
        val started = now >= first.startsAt
        val title = (if (first.exam != null) { if (started) "考试中 · " else "即将考试 · " }
            else if (started) "上课中 · " else "即将上课 · ") + courses.joinToString(" / ") { it.lesson.name }
        val description = courses.joinToString("\n") { course ->
            listOfNotNull(course.lesson.location, "${time(course.startsAt)}–${time(course.endsAt)}", course.lesson.teacher).joinToString(" · ")
        }
        val unpin = PendingIntent.getBroadcast(context, UNPIN_REQUEST,
            Intent(context, CourseReminderReceiver::class.java).setAction(ACTION_UNPIN), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description)).setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_EVENT).setOngoing(true).setOnlyAlertOnce(true)
            .setWhen(if (started) first.endsAt else first.startsAt).setShowWhen(true)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter((courses.maxOf { it.endsAt } - now).coerceAtLeast(1L))
            .addAction(0, "收起本次实况", unpin)
        if (Build.VERSION.SDK_INT >= 36 && started) builder.setRequestPromotedOngoing(true)
        try { notifications.notify(LIVE_TAG, LIVE_NOTIFICATION, builder.build()) }
        catch (_: SecurityException) { }
    }

    private fun clearOwnedNotifications() {
        notifications.activeNotifications.filter { it.tag == REMINDER_TAG || it.tag == LIVE_TAG }
            .forEach { notifications.cancel(it.tag, it.id) }
    }

    private fun time(at: Long): String = DateTimeFormatter.ofPattern("HH:mm").withZone(CourseReminderPlanner.zone)
        .format(Instant.ofEpochMilli(at))

    companion object {
        const val EXTRA_OPEN_SCHEDULE = "open_schedule"
        internal const val ACTION_TICK = "com.hebeu.emptyclassroom.COURSE_REMINDER_TICK"
        internal const val ACTION_UNPIN = "com.hebeu.emptyclassroom.COURSE_LIVE_UNPIN"
        private const val REMINDER_CHANNEL = "course_reminders"
        private const val LIVE_CHANNEL = "course_live"
        private const val REMINDER_TAG = "course-reminder"
        private const val LIVE_TAG = "course-live"
        private const val LIVE_NOTIFICATION = 61000
        private const val ALARM_REQUEST = 61001
        private const val UNPIN_REQUEST = 61002
        private const val OPEN_REQUEST = 61003
        private val lock = CourseReminderStore.lock
    }
}
