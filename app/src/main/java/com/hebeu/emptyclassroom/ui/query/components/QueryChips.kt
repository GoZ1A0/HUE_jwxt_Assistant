package com.hebeu.emptyclassroom.ui.query.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.domain.model.PeriodOption
import com.hebeu.emptyclassroom.domain.model.WeekOption
import com.hebeu.emptyclassroom.ui.common.Formatters
import com.hebeu.emptyclassroom.ui.theme.AppSpacing
import java.time.LocalDate

/**
 * 周次多选（1..19，FlowRow），每项显示：
 * - 「第N周」+ 真实边界日期（rqfw / 校历推导）；
 * - 本周标注「本周」；
 * - zczt="0" 的周 chips 禁用并标注「不可选」（真实协议可用性，不是 UI 臆断）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekChipFlow(
    weeks: List<WeekOption>,
    selectedWeeks: Set<Int>,
    termStartDate: LocalDate?,
    currentWeek: Int?,
    onToggleWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        weeks.forEach { week ->
            val selected = week.week in selectedWeeks
            val isCurrent = currentWeek != null && week.week == currentWeek
            val rangeText = Formatters.weekRangeText(
                week = week.week,
                dateRangeText = week.dateRangeText,
                termStartDate = termStartDate,
            )
            FilterChip(
                selected = selected,
                onClick = { onToggleWeek(week.week) },
                enabled = week.selectable,
                label = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "第${week.week}周",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(AppSpacing.xxs))
                                Text(
                                    text = "本周",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = when {
                                !week.selectable -> "不可选"
                                !rangeText.isNullOrBlank() -> rangeText
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                !week.selectable -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** 星期多选（1..7 = 周一..周日），今天标注「今天」。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayChipFlow(
    selectedWeekdays: Set<Int>,
    todayWeekday: Int,
    onToggleWeekday: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        (1..7).forEach { weekday ->
            val selected = weekday in selectedWeekdays
            FilterChip(
                selected = selected,
                onClick = { onToggleWeekday(weekday) },
                label = {
                    Column {
                        Text(
                            text = Formatters.weekdayName(weekday),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (weekday == todayWeekday) {
                            Text(
                                text = "今天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** 节次多选：按 RSDMC 分组（上午 / 下午 / 晚上），每项显示真实起止时间。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodChipSections(
    periods: List<PeriodOption>,
    selectedPeriods: Set<Int>,
    onTogglePeriod: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = periods.groupBy { it.partOfDay?.takeIf { part -> part.isNotBlank() } ?: "其他" }
        .toList()
        .sortedWith(
            compareBy { (part, _) ->
                when (part) {
                    "上午" -> 0
                    "下午" -> 1
                    "晚上" -> 2
                    else -> 3
                }
            },
        )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        groups.forEach { (part, partPeriods) ->
            val sorted = partPeriods.sortedBy { it.number }
            val rangeLabel = if (sorted.size > 1) {
                "（${sorted.first().number}-${sorted.last().number}节）"
            } else {
                ""
            }
            Text(
                text = part + rangeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                sorted.forEach { period ->
                    val selected = period.number in selectedPeriods
                    FilterChip(
                        selected = selected,
                        onClick = { onTogglePeriod(period.number) },
                        label = {
                            Column {
                                Text(
                                    text = "第${period.number}节",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                if (!period.timeRange.isNullOrBlank()) {
                                    Text(
                                        text = period.timeRange,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (periods.isEmpty()) {
            Text(
                text = "节次选项未加载，请点击右上角「刷新选项」重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
