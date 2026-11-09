package com.hebeu.emptyclassroom.ui.query

import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.ClassroomQuery
import com.hebeu.emptyclassroom.domain.model.PeriodOption
import com.hebeu.emptyclassroom.domain.model.QueryOptions
import com.hebeu.emptyclassroom.domain.model.SavedQuery
import com.hebeu.emptyclassroom.domain.model.WeekOption
import com.hebeu.emptyclassroom.ui.common.Formatters
import kotlin.math.abs

/**
 * 纯函数工厂：把 domain 选项 / 上次保存的条件 / 用户草稿换算成 UI 需要的对象。
 * 与 ViewModel 分离，便于在 JVM 单测里直接验证「默认值、恢复上次条件、非法值回退、联动重置」。
 */
object QueryStateFactory {

    /** 恢复上次条件或给出默认草稿（默认周次 = 按校历推算的本周，默认星期 = 今天）。 */
    fun buildDraft(
        options: QueryOptions,
        saved: SavedQuery?,
        todayWeekday: Int = Formatters.todayWeekday(),
    ): QueryDraft {
        val weekOptions = effectiveWeeks(options)
        val campusId = saved?.campusId
            ?.takeIf { savedCampus -> options.campuses.any { it.id == savedCampus } }
            ?: options.campuses.firstOrNull { it.id == options.defaultCampusId }?.id
            ?: options.defaultCampusId
        val buildings = options.buildingsByCampus[campusId].orEmpty()
        val buildingId = saved?.buildingId
            ?.takeIf { it.isNotBlank() && buildings.any { building -> building.id == it } }
            ?.takeIf { it.isNotBlank() }

        val weeks = saved?.weeks
            ?.filter { week -> weekOptions.any { it.week == week && it.selectable } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultWeeks(options)
        val weekdays = saved?.weekdays
            ?.filter { it in 1..7 }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(todayWeekday.coerceIn(1, 7))
        val periodNumbers = options.periods.map { it.number }.toSet()
        val periods = saved?.periods
            ?.filter { number -> periodNumbers.isEmpty() || number in periodNumbers }
            ?.toSet()
            .orEmpty()

        val categoryId = saved?.categoryId
            ?.takeIf { id -> id.isNotBlank() && options.categories.any { it.id == id } }
        val category = options.categories.firstOrNull { it.id == categoryId }
        val subCategoryId = saved?.subCategoryId?.takeIf { id ->
            id.isNotBlank() && category?.children?.any { it.id == id } == true
        }

        val savedMin = saved?.minSeats?.takeIf { it >= 0 }
        val savedMax = saved?.maxSeats?.takeIf { it >= 0 }
        val seatsValid = savedMin == null || savedMax == null || savedMin <= savedMax

        return QueryDraft(
            termKey = saved?.termKey
                ?.takeIf { key -> options.terms.any { it.key == key } }
                ?: options.terms.firstOrNull { it.key == options.selectedTermKey }?.key
                ?: options.terms.firstOrNull()?.key,
            campusId = campusId,
            buildingId = buildingId,
            categoryId = categoryId,
            subCategoryId = subCategoryId,
            weeks = weeks,
            weekdays = weekdays,
            periods = periods,
            minSeatsText = if (seatsValid) savedMin?.toString().orEmpty() else "",
            maxSeatsText = if (seatsValid) savedMax?.toString().orEmpty() else "",
            roomName = saved?.roomName.orEmpty(),
        )
    }

    /** 默认选中「按校历推算的本周」；该周不可选时选最近的可用周；校历未知时选第一个可用周。 */
    fun defaultWeeks(options: QueryOptions, today: java.time.LocalDate = java.time.LocalDate.now()): Set<Int> {
        val weekOptions = effectiveWeeks(options)
        val selectable = weekOptions.filter { it.selectable }.ifEmpty { weekOptions }
        if (selectable.isEmpty()) return emptySet()
        val derivedWeek = Formatters.currentTeachingWeek(
            termStartDate = options.termInfo.startDate,
            totalWeeks = options.termInfo.totalWeeks,
            today = today,
        ) ?: options.termInfo.currentWeek?.takeIf { it in 1..options.termInfo.totalWeeks }
        val target = derivedWeek ?: selectable.first().week
        return setOf(selectable.minByOrNull { abs(it.week - target) }?.week ?: target)
    }

    /** UI 渲染用周次列表：数据层未返回时按总周数兜底（只兜底数量，不伪造业务可用性）。 */
    fun effectiveWeeks(options: QueryOptions): List<WeekOption> {
        if (options.weeks.isNotEmpty()) return options.weeks
        val totalWeeks = options.termInfo.totalWeeks.coerceAtLeast(1)
        return (1..totalWeeks).map { week ->
            WeekOption(
                week = week,
                bitValue = week,
                dateRangeText = null,
                startDate = Formatters.inferDate(options.termInfo.startDate, week, 1),
                selectable = true,
            )
        }
    }

    /**
     * 草稿 → 查询条件。必选条件不完整/非法时返回 null（调用方展示 [QueryUiState.validationMessage]）。
     * 注意：教学楼/场地类别用 null 表示「全部」（与 ClassroomQuery 契约一致）。
     */
    fun buildQuery(
        draft: QueryDraft,
        options: QueryOptions,
        periodsForCampus: List<PeriodOption>,
    ): ClassroomQuery? {
        if (draft.weeks.isEmpty() || draft.weekdays.isEmpty() || draft.periods.isEmpty()) return null
        if (draft.minSeats != null && draft.maxSeats != null && draft.minSeats!! > draft.maxSeats!!) return null
        val term = options.terms.firstOrNull { it.key == draft.termKey }
            ?: options.terms.firstOrNull { it.key == options.selectedTermKey }
            ?: return null
        val campusId = draft.campusId ?: options.defaultCampusId
        if (campusId.isBlank()) return null
        val periodNumbers = periodsForCampus.map { it.number }.toSet()
        val periods = if (periodNumbers.isEmpty()) draft.periods else draft.periods.filter { it in periodNumbers }.toSet()
        if (periods.isEmpty()) return null
        val weekOptions = effectiveWeeks(options)
        val weeks = draft.weeks.filter { week -> weekOptions.any { it.week == week } }.toSet()
        if (weeks.isEmpty()) return null
        return ClassroomQuery(
            term = term,
            campusId = campusId,
            buildingId = draft.buildingId?.takeIf { it.isNotBlank() },
            weeks = weeks,
            weekdays = draft.weekdays.filter { it in 1..7 }.toSet(),
            periods = periods,
            categoryId = draft.categoryId?.takeIf { it.isNotBlank() },
            subCategoryId = draft.subCategoryId?.takeIf { it.isNotBlank() },
            minSeats = draft.minSeats,
            maxSeats = draft.maxSeats,
            roomName = draft.roomName.trim().takeIf { it.isNotEmpty() },
        )
    }

    /** 结果头「冻结的条件摘要」。 */
    fun buildAppliedConditions(
        query: ClassroomQuery,
        options: QueryOptions,
        campusDetails: Map<String, CampusDetail>,
    ): AppliedConditions {
        val campusName = options.campuses.firstOrNull { it.id == query.campusId }?.name ?: query.campusId
        val buildings = campusDetails[query.campusId]?.buildings
            ?: options.buildingsByCampus[query.campusId].orEmpty()
        val buildingName = query.buildingId?.let { id -> buildings.firstOrNull { it.id == id }?.name }
        val category = options.categories.firstOrNull { it.id == query.categoryId }
        val subCategory = category?.children?.firstOrNull { it.id == query.subCategoryId }
        val weeks = query.weeks.sorted()
        val weekdays = query.weekdays.sorted()
        val periods = query.periods.sorted()
        val resolvedDateText = if (weeks.size == 1 && weekdays.size == 1) {
            Formatters.inferDate(options.termInfo.startDate, weeks.first(), weekdays.first())
                ?.let { date -> "${Formatters.formatIsoDate(date)} ${Formatters.weekdayName(weekdays.first())}" }
        } else {
            null
        }
        return AppliedConditions(
            termLabel = query.term.label,
            campusName = campusName,
            buildingName = buildingName,
            weeks = weeks,
            weekdays = weekdays,
            periods = periods,
            categoryName = subCategory?.name ?: category?.name,
            minSeats = query.minSeats,
            maxSeats = query.maxSeats,
            roomName = query.roomName,
            resolvedDateText = resolvedDateText,
        )
    }

    /** 草稿 → DataStore 持久化模型（只保存稳定字段；不保存密码等敏感信息）。 */
    fun buildSavedQuery(draft: QueryDraft): SavedQuery = SavedQuery(
        termKey = draft.termKey.orEmpty(),
        campusId = draft.campusId.orEmpty(),
        buildingId = draft.buildingId?.takeIf { it.isNotBlank() },
        weeks = draft.weeks.sorted(),
        weekdays = draft.weekdays.sorted(),
        periods = draft.periods.sorted(),
        categoryId = draft.categoryId?.takeIf { it.isNotBlank() },
        subCategoryId = draft.subCategoryId?.takeIf { it.isNotBlank() },
        minSeats = draft.minSeats,
        maxSeats = draft.maxSeats,
        roomName = draft.roomName.trim().takeIf { it.isNotEmpty() },
    )

    /** 当前校区可选教学楼（含「全部」项，其 id 为 ""）。 */
    fun buildingsFor(options: QueryOptions, campusId: String?, details: Map<String, CampusDetail>): List<BuildingOption> {
        if (campusId.isNullOrBlank()) return emptyList()
        val detail = details[campusId]?.buildings
        if (!detail.isNullOrEmpty()) return detail
        return options.buildingsByCampus[campusId].orEmpty()
    }
}
