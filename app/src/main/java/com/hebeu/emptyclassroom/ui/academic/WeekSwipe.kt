package com.hebeu.emptyclassroom.ui.academic

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** The timetable itself is the page, so dragging a course or a gap follows the finger. */
@Composable
fun WeekPager(week: Int, maxWeek: Int, modifier: Modifier = Modifier, onWeek: (Int) -> Unit,
    content: @Composable (Int) -> Unit) {
    val pages = maxWeek.coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = (week - 1).coerceIn(0, pages - 1), pageCount = { pages })
    val selectedWeek by rememberUpdatedState(week)
    val changeWeek by rememberUpdatedState(onWeek)
    var movingFromControls by remember { mutableStateOf(false) }
    LaunchedEffect(week, pages) {
        val target = (week - 1).coerceIn(0, pages - 1)
        if (pager.settledPage != target) {
            movingFromControls = true
            try { pager.animateScrollToPage(target, animationSpec = tween(220)) }
            finally { if (selectedWeek == week) movingFromControls = false }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { Triple(pager.isScrollInProgress, pager.settledPage, movingFromControls) }
            .collect { (moving, page, controlled) ->
                if (!moving && !controlled && page + 1 != selectedWeek) changeWeek(page + 1)
            }
    }
    HorizontalPager(state = pager, modifier = modifier,
        key = { it }) { page -> content(page + 1) }
}

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
