package com.hebeu.emptyclassroom.ui.academic

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.weekSwipe(week: Int, maxWeek: Int, enabled: Boolean = true, onWeek: (Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(week, maxWeek) {
        var dragged = 0f
        val threshold = 48.dp.toPx()
        detectHorizontalDragGestures(onDragStart = { dragged = 0f }, onDragCancel = { dragged = 0f },
            onHorizontalDrag = { change, amount -> change.consume(); dragged += amount },
            onDragEnd = {
                if (abs(dragged) >= threshold) {
                    val next = (week + if (dragged < 0) 1 else -1).coerceIn(1, maxWeek)
                    if (next != week) onWeek(next)
                }
                dragged = 0f
            })
    }
