package com.hebeu.emptyclassroom.ui.query

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebeu.emptyclassroom.domain.model.Classroom
import com.hebeu.emptyclassroom.ui.common.components.AboutDataSourceDialog
import com.hebeu.emptyclassroom.ui.common.components.SessionExpiredDialog
import com.hebeu.emptyclassroom.ui.motion.rememberMotionSpec
import com.hebeu.emptyclassroom.ui.query.components.QueryActions
import com.hebeu.emptyclassroom.ui.query.components.QueryConditionCard
import com.hebeu.emptyclassroom.ui.query.components.conditionValidationHint
import com.hebeu.emptyclassroom.ui.result.ClassroomCard
import com.hebeu.emptyclassroom.ui.result.ClassroomDetailDialog
import com.hebeu.emptyclassroom.ui.result.LoadMoreFooter
import com.hebeu.emptyclassroom.ui.result.ResultHeader
import com.hebeu.emptyclassroom.ui.result.ResultInlineProgress
import com.hebeu.emptyclassroom.ui.result.ResultPanelContent
import com.hebeu.emptyclassroom.ui.result.ResultPanelState
import com.hebeu.emptyclassroom.ui.result.resultPanelState
import com.hebeu.emptyclassroom.ui.theme.AppSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.hebeu.emptyclassroom.ui.settings.*

/** 查询页回调集合（由 QueryRoute 从 ViewModel 绑定；Preview/UI 测试可直接构造 fake）。 */
data class QueryScreenCallbacks(
    val actions: QueryActions,
    val onStart: () -> Unit,
    val onQuery: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRetryLoadMore: () -> Unit,
    val onRefreshOptions: () -> Unit,
    val onLogout: () -> Unit,
    val onRelogin: () -> Unit,
    val onDismissSessionExpired: () -> Unit,
)

/** 有状态入口：绑定 [QueryViewModel]。 */
@Composable
fun QueryRoute(
    viewModel: QueryViewModel,
    onLogout: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
    quickLookup: QuickLookupViewModel? = null,
    menuActions: AppMenuActions? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) {
        QueryScreenCallbacks(
            actions = QueryActions(
                onTermChange = viewModel::setTerm,
                onCampusChange = viewModel::setCampus,
                onBuildingChange = viewModel::setBuilding,
                onCategoryChange = viewModel::setCategory,
                onSubCategoryChange = viewModel::setSubCategory,
                onToggleWeek = viewModel::toggleWeek,
                onToggleWeekday = viewModel::toggleWeekday,
                onTogglePeriod = viewModel::togglePeriod,
                onMinSeatsChange = viewModel::setMinSeats,
                onMaxSeatsChange = viewModel::setMaxSeats,
                onRoomNameChange = viewModel::setRoomName,
                onToggleMore = viewModel::toggleMoreExpanded,
                onClearMore = viewModel::clearMoreConditions,
                onRefreshOptions = viewModel::refreshOptions,
                onRetryCampusDetail = viewModel::retryLoadCampusDetail,
            ),
            onStart = viewModel::start,
            onQuery = viewModel::query,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onRetryLoadMore = viewModel::retryLoadMore,
            onRefreshOptions = viewModel::refreshOptions,
            onLogout = onLogout,
            onRelogin = {
                viewModel.dismissSessionExpired()
                onRelogin()
            },
            onDismissSessionExpired = viewModel::dismissSessionExpired,
        )
    }
    LaunchedEffect(viewModel) { viewModel.start() }
    QueryScreen(
        state = state,
        callbacks = callbacks,
        events = viewModel.events,
        modifier = modifier,
        quickLookupContent = { quickLookup?.let { QuickLookupRoute(it) } },
        menuActions = menuActions,
    )
}

/**
 * 查询页（无状态，Preview/截图/UI 测试均可注入任意 [QueryUiState] 渲染）。
 *
 * 结构（ui-spec §3.2）：
 *  - TopAppBar：标题 + 溢出菜单（刷新选项 / 关于与数据来源 / 退出登录）；
 *  - LazyColumn：条件卡片 → 重查进度条 → 结果头（摘要/数量/时间/来源）→ 状态面板或结果列表 → 加载更多 → 页脚说明；
 *  - bottomBar：固定「查询」按钮 + 缺失条件提示（避免顶部够不到）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryScreen(
    state: QueryUiState,
    callbacks: QueryScreenCallbacks,
    modifier: Modifier = Modifier,
    events: Flow<QueryEvent> = emptyFlow(),
    quickLookupContent: @Composable () -> Unit = {},
    menuActions: AppMenuActions? = null,
) {
    val motion = rememberMotionSpec()
    val snackbarHostState = remember { SnackbarHostState() }
    var detailRoom by remember { mutableStateOf<Classroom?>(null) }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is QueryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // 系统返回：先收「更多条件」；对话框由各自的 onDismissRequest 处理。
    BackHandler(enabled = state.isMoreExpanded) { callbacks.actions.onToggleMore() }

    detailRoom?.let { room ->
        ClassroomDetailDialog(room = room, onDismiss = { detailRoom = null })
    }
    if (state.sessionExpired) {
        SessionExpiredDialog(
            onRelogin = callbacks.onRelogin,
            onDismiss = callbacks.onDismissSessionExpired,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "空闲教室查询",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = callbacks.onRefreshOptions) { Icon(Icons.Outlined.Refresh, "刷新条件选项") }
                    AppOverflowMenu(menuActions ?: AppMenuActions({}, {}, {}))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            QueryBottomBar(
                state = state,
                onQuery = callbacks.onQuery,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val panel = state.resultPanelState()
            val listState = rememberLazyListState()

            // 滚动到底部附近自动加载下一页（ViewModel 内部有 isQuerying/isLoadingMore/hasMore 三道防重入）。
            val nearBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
                }
            }
            LaunchedEffect(nearBottom, panel, state.hasMore, state.isQuerying, state.isLoadingMore) {
                if (nearBottom && panel is ResultPanelState.Success &&
                    state.hasMore && !state.isQuerying && !state.isLoadingMore
                ) {
                    callbacks.onLoadMore()
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = AppSpacing.contentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = AppSpacing.screenHorizontal,
                    end = AppSpacing.screenHorizontal,
                    top = AppSpacing.md,
                    bottom = AppSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.listGap),
            ) {
                item(key = "current-quick-lookup") { quickLookupContent() }
                item(key = "condition-card") {
                    QueryConditionCard(state = state, actions = callbacks.actions)
                }

                // 重新查询：保留旧结果，仅顶部细进度条（ui-spec §4）
                if (state.isQuerying && state.result != null) {
                    item(key = "requery-progress") {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(motion.tween(motion.shortMs)),
                            exit = fadeOut(motion.tween(motion.shortMs)),
                        ) {
                            ResultInlineProgress()
                        }
                    }
                }

                if (state.hasResultHeader && state.applied != null && state.result != null) {
                    item(key = "result-header") {
                        ResultHeader(
                            applied = state.applied,
                            totalCount = state.result?.total,
                            queriedAt = state.result?.queriedAt,
                            isQuerying = state.isQuerying,
                            isEmpty = state.emptyResult,
                            hasError = state.resultError != null,
                        )
                    }
                }

                if (panel != ResultPanelState.Success) {
                    item(key = "result-panel") {
                        ResultPanelContent(
                            state = panel,
                            onRetry = callbacks.onRetry,
                            onRelogin = callbacks.onRelogin,
                        )
                    }
                }

                if (panel == ResultPanelState.Success && state.result != null) {
                    items(
                        items = state.result.items,
                        key = { room -> "${room.id}-${room.code}-${room.name}" },
                    ) { room ->
                        ClassroomCard(
                            room = room,
                            onClick = { detailRoom = room },
                            modifier = Modifier.animateItem(
                                fadeInSpec = motion.fadeSpec(),
                                placementSpec = motion.placementSpec(),
                                fadeOutSpec = motion.fadeSpec(),
                            ),
                        )
                    }
                    item(key = "load-more") {
                        LoadMoreFooter(
                            isLoadingMore = state.isLoadingMore,
                            loadMoreError = state.loadMoreError,
                            hasMore = state.hasMore,
                            totalCount = state.result?.total ?: 0,
                            onRetry = callbacks.onRetryLoadMore,
                        )
                    }
                }


            }
        }
    }
}

/** 底部固定操作区：缺失条件提示 + 查询按钮（loading 时 Crossfade + 禁用）。 */
@Composable
private fun QueryBottomBar(
    state: QueryUiState,
    onQuery: () -> Unit,
) {
    val motion = rememberMotionSpec()
    val hint = state.conditionValidationHint()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(motion.tween(motion.standardMs)) +
                    expandVertically(motion.tween(motion.standardMs)),
                exit = fadeOut(motion.tween(motion.shortMs)) +
                    shrinkVertically(motion.tween(motion.shortMs)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                    Text(
                        text = hint.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(
                onClick = onQuery,
                enabled = state.options != null && !state.isQuerying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSpacing.primaryButtonHeight),
                shape = MaterialTheme.shapes.large,
            ) {
                Crossfade(
                    targetState = state.isQuerying,
                    animationSpec = motion.tween(motion.shortMs),
                    label = "queryButton",
                ) { querying ->
                    if (querying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text(text = "查询中…", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text(text = "查询", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
