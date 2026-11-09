package com.hebeu.emptyclassroom.ui.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.domain.model.Classroom
import com.hebeu.emptyclassroom.ui.theme.AppSpacing

/**
 * 结果条目：教室名称突出（第一行加粗），第二行教学楼 · 校区，第三行次要信息（类别/座位/考试座位/使用部门）。
 * 只渲染真实存在的字段（domain 模型里的可空字段为空时不显示），不补默认值、不猜测。
 */
@Composable
fun ClassroomCard(
    room: Classroom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            // ---- 第一行：教室名 + 座位 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.MeetingRoom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = AppSpacing.xs)
                        .size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!room.code.isNullOrBlank() && room.code != room.name) {
                        Text(
                            text = "编号 ${room.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                room.seats?.let { seats ->
                    SeatsBadge(seats = seats)
                }
            }

            // ---- 第二行：教学楼 · 校区 ----
            val locationText = listOfNotNull(
                room.buildingName?.takeIf { it.isNotBlank() },
                room.campusName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (locationText.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Apartment,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = AppSpacing.xxs)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ---- 第三行：类别 / 考试座位 / 使用部门（真实存在才展示） ----
            val secondary = buildList {
                room.categoryName?.takeIf { it.isNotBlank() }?.let { add("类别：$it") }
                room.examSeats?.let { add("考试座位：$it") }
                room.department?.takeIf { it.isNotBlank() }?.let { add("使用部门：$it") }
            }
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeatsBadge(seats: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Chair,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(14.dp),
            )
            Text(text = "$seats 座", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 教室详情对话框（可选功能：点击条目查看全部真实字段）。 */
@Composable
fun ClassroomDetailDialog(
    room: Classroom,
    onDismiss: () -> Unit,
    availability: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(text = room.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                DetailRow(label = "连续空闲", value = availability)
                DetailRow(label = "场地编号", value = room.code)
                DetailRow(label = "教学楼", value = room.buildingName)
                DetailRow(label = "校区", value = room.campusName)
                DetailRow(label = "场地类别", value = room.categoryName)
                DetailRow(label = "座位数", value = room.seats?.toString())
                DetailRow(label = "考试座位数", value = room.examSeats?.toString())
                DetailRow(label = "使用部门", value = room.department)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "关闭") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(AppSpacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.6f),
        )
    }
}
