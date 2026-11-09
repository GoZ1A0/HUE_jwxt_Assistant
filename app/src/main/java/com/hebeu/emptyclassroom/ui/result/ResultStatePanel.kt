package com.hebeu.emptyclassroom.ui.result

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.ui.common.ErrorKind
import com.hebeu.emptyclassroom.ui.common.UiError
import com.hebeu.emptyclassroom.ui.common.components.InfoStatePanel
import com.hebeu.emptyclassroom.ui.common.components.SkeletonRoomCard
import com.hebeu.emptyclassroom.ui.query.QueryUiState
import com.hebeu.emptyclassroom.ui.theme.AppSpacing

/** 结果区状态机：Idle / Loading(骨架) / Empty / Error / SessionExpired；Success 由列表本身表达。 */
sealed interface ResultPanelState {
    data object Idle : ResultPanelState
    data object Loading : ResultPanelState
    data object Success : ResultPanelState
    data object Empty : ResultPanelState
    data class Error(val error: UiError) : ResultPanelState
    data object SessionExpired : ResultPanelState
}

/** 从 [QueryUiState] 推导结果面板状态（顺序即优先级）。 */
fun QueryUiState.resultPanelState(): ResultPanelState = when {
    sessionExpired -> ResultPanelState.SessionExpired
    isQuerying && result == null -> ResultPanelState.Loading
    resultError != null -> ResultPanelState.Error(resultError)
    emptyResult -> ResultPanelState.Empty
    result != null && result.items.isNotEmpty() -> ResultPanelState.Success
    else -> ResultPanelState.Idle
}

/** 状态面板切换动画（ui-spec §4：AnimatedContent fade + scale 0.98→1，220ms）。 */
@Composable
fun ResultPanelContent(
    state: ResultPanelState,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = com.hebeu.emptyclassroom.ui.motion.rememberMotionSpec()
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(motion.tween(motion.standardMs)) +
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = motion.tween(motion.standardMs),
                )) togetherWith
                (fadeOut(motion.tween(motion.shortMs)) +
                    scaleOut(
                        targetScale = 0.98f,
                        animationSpec = motion.tween(motion.shortMs),
                    ))
        },
        label = "resultPanel",
    ) { panel ->
        when (panel) {
            ResultPanelState.Idle -> InfoStatePanel(
                icon = Icons.Outlined.Search,
                title = "填写条件开始查询",
                message = "选择周次、星期、节次后点击下方「查询」，即可查看这些时间范围内空闲的教室。",
            )
            ResultPanelState.Loading -> LoadingSkeleton()
            ResultPanelState.Success -> Unit
            ResultPanelState.Empty -> InfoStatePanel(
                icon = Icons.Outlined.SearchOff,
                title = "当前条件下没有空闲教室",
                message = "试试放宽条件：减少周次/星期、换一个教学楼，或调整节次范围后重新查询。",
            )
            ResultPanelState.SessionExpired -> InfoStatePanel(
                icon = Icons.Outlined.ErrorOutline,
                title = "登录已过期",
                message = "登录状态已失效，请重新登录后继续查询。",
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                actionLabel = "重新登录",
                onAction = onRelogin,
            )
            is ResultPanelState.Error -> {
                val error = panel.error
                InfoStatePanel(
                    icon = errorIcon(error),
                    title = error.title,
                    message = error.message,
                    iconTint = if (error.kind == ErrorKind.TERM_CLOSED) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    iconContainerColor = if (error.kind == ErrorKind.TERM_CLOSED) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    actionLabel = if (error.retryable) "重试" else null,
                    onAction = if (error.retryable) onRetry else null,
                )
            }
        }
    }
}

/** 首次查询无旧结果时的骨架（3 条），避免整页白屏。 */
@Composable
private fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        repeat(3) {
            SkeletonRoomCard()
        }
    }
}

private fun errorIcon(error: UiError): ImageVector = when (error.kind) {
    ErrorKind.NETWORK -> Icons.Outlined.WifiOff
    ErrorKind.TIMEOUT -> Icons.Outlined.Schedule
    ErrorKind.SERVER -> Icons.Outlined.CloudOff
    ErrorKind.PARSE -> Icons.Outlined.ErrorOutline
    ErrorKind.SESSION_EXPIRED -> Icons.Outlined.ErrorOutline
    ErrorKind.TERM_CLOSED -> Icons.Outlined.Schedule
    ErrorKind.OTHER -> Icons.Outlined.ErrorOutline
}

/** 结果区顶部细进度条（重查时保留旧结果，仅顶部提示）。 */
@Composable
fun ResultInlineProgress(modifier: Modifier = Modifier) {
    androidx.compose.material3.LinearProgressIndicator(
        modifier = modifier.fillMaxWidth(),
    )
}

/** 底部「加载更多」区域：加载中 / 失败重试 / 已全部加载。 */
@Composable
fun LoadMoreFooter(
    isLoadingMore: Boolean,
    loadMoreError: Boolean,
    hasMore: Boolean,
    totalCount: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        when {
            isLoadingMore -> {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.padding(AppSpacing.xs),
                    strokeWidth = 2.dp,
                )
                androidx.compose.material3.Text(
                    text = "正在加载更多…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            loadMoreError -> androidx.compose.material3.TextButton(onClick = onRetry) {
                androidx.compose.material3.Text(text = "加载更多失败，点击重试")
            }
            !hasMore -> androidx.compose.material3.Text(
                text = "已显示全部 $totalCount 间教室",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
