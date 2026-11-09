package com.hebeu.emptyclassroom.ui.query.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.ui.common.Formatters
import com.hebeu.emptyclassroom.ui.common.components.InlineErrorCard
import com.hebeu.emptyclassroom.ui.common.components.SectionHeader
import com.hebeu.emptyclassroom.ui.motion.MotionSpec
import com.hebeu.emptyclassroom.ui.motion.rememberMotionSpec
import com.hebeu.emptyclassroom.ui.query.QueryUiState
import com.hebeu.emptyclassroom.ui.theme.AppSpacing

/** 查询页所有用户操作的回调集合（由 QueryScreen 从 ViewModel 绑定，便于 UI 测试注入）。 */
data class QueryActions(
    val onTermChange: (String) -> Unit,
    val onCampusChange: (String) -> Unit,
    val onBuildingChange: (String?) -> Unit,
    val onCategoryChange: (String?) -> Unit,
    val onSubCategoryChange: (String?) -> Unit,
    val onToggleWeek: (Int) -> Unit,
    val onToggleWeekday: (Int) -> Unit,
    val onTogglePeriod: (Int) -> Unit,
    val onMinSeatsChange: (String) -> Unit,
    val onMaxSeatsChange: (String) -> Unit,
    val onRoomNameChange: (String) -> Unit,
    val onToggleMore: () -> Unit,
    val onClearMore: () -> Unit,
    val onRefreshOptions: () -> Unit,
    val onRetryCampusDetail: () -> Unit,
)

/**
 * 查询条件卡片：
 *  常用：学年学期 / 校区（分段按钮）/ 教学楼 / 周次 / 星期 / 节次；
 *  更多条件（折叠，AnimatedVisibility + animateContentSize）：场地类别、二级类别、座位数区间、场地名称。
 */
@Composable
fun QueryConditionCard(
    state: QueryUiState,
    actions: QueryActions,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionSpec()
    val options = state.options
    val todayWeekday = remember { Formatters.todayWeekday() }
    val currentWeek = remember(options?.termInfo?.startDate, options?.termInfo?.totalWeeks) {
        Formatters.currentTeachingWeek(
            termStartDate = options?.termInfo?.startDate,
            totalWeeks = options?.termInfo?.totalWeeks,
        )
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(motion.tween(motion.standardMs)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.cardInner),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            SectionHeader(
                title = "查询条件",
                subtitle = "数据来源：河北工程大学教务系统",
            )

            // ---- 选项加载 / 失败 ----
            if (options == null || state.isLoadingOptions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (options == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                    }
                    Text(
                        text = if (options == null) "正在加载查询选项…" else "正在刷新查询选项…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.optionsError?.let { error ->
                InlineErrorCard(
                    message = error.message,
                    title = error.title,
                    onRetry = actions.onRefreshOptions,
                )
            }
            if (options == null) {
                // 首次加载失败时没有条件可渲染，只留错误与刷新入口（由底部按钮区展示重试）。
                return@Column
            }

            // ---- 学年学期 ----
            DropdownSelector(
                label = "学年学期",
                options = options.terms,
                selected = state.selectedTerm,
                optionLabel = { it.label },
                onSelect = { term -> actions.onTermChange(term.key) },
                leadingIcon = Icons.Outlined.CalendarMonth,
                placeholder = "请选择学期",
            )

            // ---- 校区（SingleChoiceSegmentedButtonRow） ----
            ConditionSectionTitle(title = "校区")
            CampusSegmentedSelector(
                campuses = options.campuses,
                selectedCampusId = state.selectedCampus?.id,
                onSelect = actions.onCampusChange,
            )

            // ---- 教学楼（随校区联动，含「全部」） ----
            BuildingDropdown(
                buildings = state.buildingsForSelectedCampus,
                selectedBuildingId = state.draft.buildingId,
                isLoading = state.isCampusDetailLoading,
                errorText = state.campusDetailError,
                onSelect = { building: BuildingOption -> actions.onBuildingChange(building.id) },
                onRetry = actions.onRetryCampusDetail,
            )

            // ---- 周次 ----
            ConditionSection(
                title = "周次",
                icon = Icons.Outlined.CalendarMonth,
                summary = Formatters.formatWeeksCompact(state.draft.weeks),
                badge = if (options.termInfo.startDate != null) "按校历推算" else null,
                hint = "第 1-${options.termInfo.totalWeeks} 周，可多选",
            ) {
                WeekChipFlow(
                    weeks = state.weekOptions,
                    selectedWeeks = state.draft.weeks,
                    termStartDate = options.termInfo.startDate,
                    currentWeek = currentWeek,
                    onToggleWeek = actions.onToggleWeek,
                )
            }

            // ---- 星期 ----
            ConditionSection(
                title = "星期",
                icon = Icons.Outlined.CalendarMonth,
                summary = Formatters.formatWeekdays(state.draft.weekdays),
                hint = "可多选",
            ) {
                WeekdayChipFlow(
                    selectedWeekdays = state.draft.weekdays,
                    todayWeekday = todayWeekday,
                    onToggleWeekday = actions.onToggleWeekday,
                )
            }

            // ---- 节次（按上午/下午/晚上分组，显示真实时间） ----
            ConditionSection(
                title = "节次",
                icon = Icons.Outlined.CalendarMonth,
                summary = Formatters.formatPeriodsCompact(state.draft.periods),
                hint = "可多选，按教务节次",
            ) {
                PeriodChipSections(
                    periods = state.periodsForSelectedCampus,
                    selectedPeriods = state.draft.periods,
                    onTogglePeriod = actions.onTogglePeriod,
                )
            }

            // ---- 更多条件（折叠） ----
            MoreConditionsSection(
                state = state,
                actions = actions,
            )
        }
    }
}

/** 区块标题 + 已选摘要 + 可选徽标 + 提示。 */
@Composable
private fun ConditionSection(
    title: String,
    summary: String?,
    icon: ImageVector,
    hint: String? = null,
    badge: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(AppSpacing.xxs))
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (badge != null) {
                Spacer(Modifier.width(AppSpacing.xs))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun ConditionSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 「更多条件」折叠区：常见次要条件（类别/二级类别/座位区间/场地名称）。 */
@Composable
private fun MoreConditionsSection(
    state: QueryUiState,
    actions: QueryActions,
) {
    val motion: MotionSpec = rememberMotionSpec()
    val options = state.options ?: return
    val expanded = state.isMoreExpanded
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.tween(motion.standardMs),
        label = "moreConditionsChevron",
    )
    val selectedCount = state.draft.moreConditionsCount
    val selectedCategory = options.categories.firstOrNull {
        it.id == (state.draft.categoryId ?: "")
    }
    val subCategories = selectedCategory?.children.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = actions.onToggleMore),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(text = "更多条件", style = MaterialTheme.typography.titleSmall)
                if (selectedCount > 0) {
                    Spacer(Modifier.width(AppSpacing.xs))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "已选 $selectedCount 项",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起更多条件" else "展开更多条件",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(motion.tween(motion.standardMs)) +
                expandVertically(motion.tween(motion.standardMs)),
            exit = fadeOut(motion.tween(motion.shortMs)) +
                shrinkVertically(motion.tween(motion.shortMs)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                // 场地类别（已含「全部」，直接渲染）
                DropdownSelector(
                    label = "场地类别",
                    options = options.categories,
                    selected = selectedCategory,
                    optionLabel = { it.name.ifBlank { "全部" } },
                    onSelect = { category -> actions.onCategoryChange(category.id) },
                    leadingIcon = Icons.Outlined.Category,
                )
                // 二级类别（本部署多数为空；仅当真实存在时展示）
                if (subCategories.isNotEmpty()) {
                    DropdownSelector(
                        label = "场地二级类别",
                        options = subCategories,
                        selected = subCategories.firstOrNull { it.id == (state.draft.subCategoryId ?: "") },
                        optionLabel = { it.name.ifBlank { "全部" } },
                        onSelect = { category -> actions.onSubCategoryChange(category.id) },
                    )
                }

                // 座位数区间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = state.draft.minSeatsText,
                        onValueChange = actions.onMinSeatsChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("座位数下限") },
                        singleLine = true,
                        isError = state.hasInvalidSeatRange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.EventSeat,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = state.draft.maxSeatsText,
                        onValueChange = actions.onMaxSeatsChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("座位数上限") },
                        singleLine = true,
                        isError = state.hasInvalidSeatRange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                if (state.hasInvalidSeatRange) {
                    Text(
                        text = "座位数下限不能大于上限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // 场地名称/编号
                OutlinedTextField(
                    value = state.draft.roomName,
                    onValueChange = actions.onRoomNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("场地名称 / 编号") },
                    placeholder = { Text("可按场地名称和编号搜索") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    supportingText = { Text("可选，模糊匹配") },
                    shape = MaterialTheme.shapes.medium,
                )

                if (selectedCount > 0) {
                    TextButton(
                        onClick = actions.onClearMore,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(text = "清除更多条件")
                    }
                }
            }
        }
    }
}

/** 条件区按钮（供 QueryScreen 的 bottomBar 使用）共用的条件是否完整提示。 */
fun QueryUiState.conditionValidationHint(): String? = when {
    validationMessage != null -> validationMessage
    missingSelectionLabels.isNotEmpty() -> "请至少选择 1 个" + missingSelectionLabels.joinToString("、")
    hasInvalidSeatRange -> "座位数下限不能大于上限"
    else -> null
}
