package com.hebeu.emptyclassroom.domain.model

import kotlinx.serialization.Serializable

/** 用户主动开启的本机课表提醒；不包含账号和登录资料。 */
@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    val minutesBefore: Int = 10,
    val ongoingEnabled: Boolean = false,
    val exactTiming: Boolean = true,
    val endEnabled: Boolean = false,
    val minutesBeforeEnd: Int = 0,
    val examEnabled: Boolean = false,
    val examDayBefore: Boolean = true,
    val examMinutesBefore: Int = 60,
) {
    val active: Boolean get() = enabled || ongoingEnabled || endEnabled || examEnabled
    fun normalized() = copy(minutesBefore = minutesBefore.coerceIn(0, 120), minutesBeforeEnd = minutesBeforeEnd.coerceIn(0, 30), examMinutesBefore = examMinutesBefore.coerceIn(0, 180))
}
