package com.hebeu.emptyclassroom.ui.query

import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.CampusOption
import com.hebeu.emptyclassroom.domain.model.ClassroomQuery
import com.hebeu.emptyclassroom.domain.model.PeriodOption
import com.hebeu.emptyclassroom.domain.model.QueryOptions
import com.hebeu.emptyclassroom.domain.model.QueryResult
import com.hebeu.emptyclassroom.domain.model.TermOption
import com.hebeu.emptyclassroom.domain.model.WeekOption
import com.hebeu.emptyclassroom.ui.common.UiError

/**
 * 可编辑的查询草稿。与 domain 的 [ClassroomQuery] 分开：这里保存 UI 临时态
 * （文本框内容、可能暂不合法的座位区间），提交时才校验并转成 [ClassroomQuery]。
 */
data class QueryDraft(
    val termKey: String? = null,
    val campusId: String? = null,
    val buildingId: String? = null,      // null = 全部
    val categoryId: String? = null,      // null = 全部
    val subCategoryId: String? = null,   // null = 全部
    val weeks: Set<Int> = emptySet(),
    val weekdays: Set<Int> = emptySet(),
    val periods: Set<Int> = emptySet(),
    val minSeatsText: String = "",
    val maxSeatsText: String = "",
    val roomName: String = "",
) {
    val minSeats: Int? get() = minSeatsText.trim().toIntOrNull()
    val maxSeats: Int? get() = maxSeatsText.trim().toIntOrNull()

    /** 已选择的「更多条件」数量（用于折叠区徽标）。 */
    val moreConditionsCount: Int
        get() = listOf(
            categoryId?.takeIf { it.isNotEmpty() },
            subCategoryId?.takeIf { it.isNotEmpty() },
            minSeats?.toString(),
            maxSeats?.toString(),
            roomName.trim().takeIf { it.isNotEmpty() },
        ).count { it != null }
}

/**
 * 一次成功查询时「冻结」的条件摘要：结果头始终显示产生该结果的条件，
 * 而不是用户正在编辑但尚未提交的草稿（ui-spec §3.2）。
 */
data class AppliedConditions(
    val termLabel: String,
    val campusName: String,
    val buildingName: String?,
    val weeks: List<Int>,
    val weekdays: List<Int>,
    val periods: List<Int>,
    val categoryName: String?,
    val minSeats: Int?,
    val maxSeats: Int?,
    val roomName: String?,
    /** 「按校历：2026-11-02 周一」；仅当恰好选了一个周次 + 一个星期日且学期开始日已知时非空。 */
    val resolvedDateText: String?,
)

/**
 * 查询页完整 UI 状态（architecture.md §5 + 本次实现所需扩展）。
 *
 * 结果区状态由以下字段组合表达（见 [QueryScreen] 的 ResultPanelState）：
 * Idle / Loading / Success / Empty / Error / SessionExpired。
 */
data class QueryUiState(
    val options: QueryOptions? = null,
    val isLoadingOptions: Boolean = false,
    val optionsError: UiError? = null,
    val draft: QueryDraft = QueryDraft(),
    /** 切校区时按需加载的楼号/节次缓存（key = campusId）。 */
    val campusDetails: Map<String, CampusDetail> = emptyMap(),
    val isCampusDetailLoading: Boolean = false,
    val campusDetailError: String? = null,
    val isMoreExpanded: Boolean = false,
    val isQuerying: Boolean = false,
    /** 当前生效（最近一次提交）的查询；用于分页 loadMore 与重试。 */
    val activeQuery: ClassroomQuery? = null,
    /** 保留最近一次成功结果；重新查询时不清空（仅顶部进度条）。 */
    val result: QueryResult? = null,
    val applied: AppliedConditions? = null,
    val resultError: UiError? = null,
    val emptyResult: Boolean = false,
    val sessionExpired: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
    val hasMore: Boolean = false,
    /** 点击查询但必选条件不完整时的行内提示。 */
    val validationMessage: String? = null,
) {
    val selectedTerm: TermOption?
        get() {
            val opts = options ?: return null
            return opts.terms.firstOrNull { it.key == draft.termKey } ?: opts.terms.firstOrNull()
        }

    val selectedCampus: CampusOption?
        get() {
            val opts = options ?: return null
            return opts.campuses.firstOrNull { it.id == draft.campusId } ?: opts.campuses.firstOrNull()
        }

    /** 当前校区可选教学楼（含「全部」）；优先用切校区时拉到的 detail，其次用 options 预取结果。 */
    val buildingsForSelectedCampus: List<BuildingOption>
        get() {
            val campusId = draft.campusId ?: return emptyList()
            val fromDetail = campusDetails[campusId]?.buildings
            if (!fromDetail.isNullOrEmpty()) return fromDetail
            return options?.buildingsByCampus?.get(campusId).orEmpty()
        }

    /** 当前校区节次（含真实时间）；options.periods 是默认校区的，detail 是切校区后刷新到的。 */
    val periodsForSelectedCampus: List<PeriodOption>
        get() {
            val campusId = draft.campusId ?: return emptyList()
            val fromDetail = campusDetails[campusId]?.periods
            if (!fromDetail.isNullOrEmpty()) return fromDetail
            return options?.periods.orEmpty()
        }

    /** 周次：数据层未返回时用总周数兜底（只兜底数量，不伪造可用性）。 */
    val weekOptions: List<WeekOption>
        get() {
            val opts = options ?: return emptyList()
            if (opts.weeks.isNotEmpty()) return opts.weeks
            return (1..opts.termInfo.totalWeeks.coerceAtLeast(1)).map { week ->
                WeekOption(
                    week = week,
                    bitValue = week,
                    dateRangeText = null,
                    startDate = null,
                    selectable = true,
                )
            }
        }

    /** 缺失的必选条件（周次/星期/节次至少各 1 个，ui-spec 与教务页一致）。 */
    val missingSelectionLabels: List<String>
        get() = buildList {
            if (draft.weeks.isEmpty()) add("周次")
            if (draft.weekdays.isEmpty()) add("星期")
            if (draft.periods.isEmpty()) add("节次")
        }

    /** 座位数区间是否非法（下限 > 上限）。 */
    val hasInvalidSeatRange: Boolean
        get() {
            val min = draft.minSeats
            val max = draft.maxSeats
            return min != null && max != null && min > max
        }

    /** 查询按钮是否可点击：选项就绪、不在加载/查询中。 */
    val canSubmit: Boolean
        get() = options != null && !isLoadingOptions && !isQuerying

    /** 是否已有可展示的结果头（有结果、空结果或错误时都显示条件摘要）。 */
    val hasResultHeader: Boolean
        get() = result != null || emptyResult || resultError != null
}

/** 一次性事件（Snackbar 等），避免把一次性提示塞进 StateFlow 造成重复消费。 */
sealed interface QueryEvent {
    data class ShowMessage(val text: String) : QueryEvent
}
