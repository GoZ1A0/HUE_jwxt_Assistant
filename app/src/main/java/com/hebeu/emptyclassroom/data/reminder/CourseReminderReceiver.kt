package com.hebeu.emptyclassroom.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        executor.execute {
            try {
                val controller = CourseReminderController(context)
                if (intent.action == CourseReminderController.ACTION_UNPIN) controller.unpin()
                else controller.handleAlarm()
            } finally { pending.finish() }
        }
    }
    internal companion object { val executor = Executors.newSingleThreadExecutor() }
}

/** 系统重启、时间变更或授权变化后，根据本机持久化课表重新安排最近事件。 */
class CourseReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CourseReminderReceiver.executor.execute {
            try { CourseReminderController(context).reschedule() }
            finally { pending.finish() }
        }
    }
}
