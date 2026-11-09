package com.hebeu.emptyclassroom.ui.result

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.ui.common.Formatters
import com.hebeu.emptyclassroom.ui.motion.MotionSpec
import com.hebeu.emptyclassroom.ui.motion.rememberMotionSpec
import com.hebeu.emptyclassroom.ui.query.AppliedConditions
import com.hebeu.emptyclassroom.ui.theme.AppSpacing

/**
 * 结果头（ui-spec §3.2 必须展示）：
 *  条件摘要（学期/校区/教学楼/周次/星期/节次 + 「按校历：日期 星期」）
 *  + 结果数量 + 查询时间 + 数据来源。
 *
 * 条件摘要来自「冻结」的 [AppliedConditions]，而不是用户正在编辑的草稿；
 * 重新查询进行中（[isQuerying]）时仍显示上一次的条件，避免闪烁。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultHeader(
    applied: AppliedConditions,
    totalCount: Int?,
    queriedAt: Long?,
    isQuerying: Boolean,
    isEmpty: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionSpec()
    val countText = when {
        hasError -> "查询失败"
        isQuerying && totalCount == null -> "查询中…"
        isEmpty -> "未找到符合条件的教室"
        totalCount != null -> "共 $totalCount 间空闲教室"
        else -> "查询中…"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "查询结果", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = countText,
                transitionSpec = {
                    fadeIn(motion.tween(motion.standardMs)) togetherWith
                        fadeOut(motion.tween(motion.shortMs))
                },
                label = "resultCount",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        // ---- 条件摘要 ----
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            SummaryChip(text = applied.termLabel)
            SummaryChip(text = applied.campusName)
            if (!applied.buildingName.isNullOrBlank()) {
                SummaryChip(text = applied.buildingName)
            }
            SummaryChip(text = Formatters.formatWeeksCompact(applied.weeks))
            SummaryChip(text = Formatters.formatWeekdays(applied.weekdays))
            SummaryChip(text = Formatters.formatPeriodsCompact(applied.periods))
            if (!applied.categoryName.isNullOrBlank()) {
                SummaryChip(text = applied.categoryName)
            }
            applied.minSeats?.let { min ->
                SummaryChip(
                    text = if (applied.maxSeats != null) {
                        "座位 $min-${applied.maxSeats}"
                    } else {
                        "座位 ≥ $min"
                    },
                )
            }
            applied.maxSeats?.let { max ->
                if (applied.minSeats == null) SummaryChip(text = "座位 ≤ $max")
            }
            if (!applied.roomName.isNullOrBlank()) {
                SummaryChip(text = "名称：${applied.roomName}")
            }
        }

        // ---- 按校历折算日期（仅当恰好一周 + 一天，展示不推断）----
        if (!applied.resolvedDateText.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "按校历：${applied.resolvedDateText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ---- 查询时间 + 数据来源 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (queriedAt != null) {
                    "${Formatters.formatTimeOfDay(queriedAt)} 查询"
                } else {
                    "尚未查询"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "数据来源：河北工程大学教务系统",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 只读条件摘要 chip（不是操作，避免 AssistChip 的点击语义）。 */
@Composable
private fun SummaryChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
