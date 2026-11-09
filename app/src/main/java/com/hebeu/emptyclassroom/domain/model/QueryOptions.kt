package com.hebeu.emptyclassroom.domain.model

import java.time.LocalDate

// ===== 学年学期 =====

/** 学年学期选项，来自页面 `select#dm_cx` 的 option（value 形如 "2026-3"）。 */
data class TermOption(
    val key: String,      // "2026-3"（页面 dm_cx 的 value）
    val xnm: String,      // "2026"（学年）
    val xqm: String,      // "3"（学期：3=第1学期 / 12=第2学期 / 16=第3学期）
    val label: String,    // "2026-2027-1"
)

/**
 * 当前学期校历信息，来源于 `POST /cdjy/cdjy_cxQtlb.html` 的 `dqzcxq`。
 *
 * 日期推导统一使用 [startDate]：`第N周星期D的日期 = startDate + (N-1)*7 + (D-1)`（周一=1）。
 */
data class TermInfo(
    val label: String,              // "2026-2027-1"
    val totalWeeks: Int,            // 19
    val currentWeek: Int?,          // 服务端下发的当前周（DQZC）；⚠️ 实测配置陈旧，UI 不要用它推算日期
    val startDate: LocalDate?,      // 学期开始日期（ZXRQ，实测为周一）
    val endDate: LocalDate?,        // 学期结束日期（ZDRQ）
)

// ===== 校区 / 楼号 / 场地类别 =====

/** 校区，来自页面 `select#xqh_id`。 */
data class CampusOption(val id: String, val name: String)          // xqh_id

/** 教学楼/楼号。id 为空字符串表示「全部」。 */
data class BuildingOption(val id: String, val name: String)        // lh

/** 场地类别，来自页面 `select#cdlb_id`；children 来自二级类别联动接口（本部署恒为空）。 */
data class VenueCategory(
    val id: String,                  // cdlb_id
    val name: String,
    val children: List<VenueCategory> = emptyList(), // cdejlb_id
)

// ===== 周次 / 节次 =====

/** 周次选项：来自 cdjy_cxQtlb 的 nxqzcList；bitValue 即页面 dxqzc（用于 zcd 位掩码）。 */
data class WeekOption(
    val week: Int,                    // 显示用周次（zc）
    val bitValue: Int,                // 位掩码用值（dxqzc，通常等于 week）
    val dateRangeText: String?,       // 真实 rqfw，如 "11/02-11/09"（仅展示参考，不是闭区间）
    val startDate: LocalDate?,        // 由学期开始日期与 week 推导（仅展示/筛选辅助）
    val selectable: Boolean,          // zczt != "0"
)

/** 节次选项：来自 cdjy_cxXqjc 的 jcList；位掩码 2^(number-1)。 */
data class PeriodOption(
    val number: Int,                  // JCMC（1..10）
    val timeRange: String?,           // SJD，如 "08:30-09:15"
    val partOfDay: String?,           // RSDMC：上午/下午/晚上
    val periodsPerBlock: Int?,        // RSDZJS（该半天内含几小节，仅展示参考）
)

/** 切换校区时按需加载的楼号 + 节次（GET /cdjy/cdjy_cxXqjc.html）。 */
data class CampusDetail(
    val campusId: String,
    val buildings: List<BuildingOption>,
    val periods: List<PeriodOption>,
)

// ===== 选项总集 =====

/**
 * 查询页所需的全部选项。
 *
 * 约定：`buildingsByCampus` 的每个列表、`CampusDetail.buildings`、`categories`
 * **都已包含 id 为空字符串的「全部」项**，UI 直接渲染即可，不要重复添加。
 */
data class QueryOptions(
    val terms: List<TermOption>,
    val selectedTermKey: String,     // 网站默认选中学期
    val termInfo: TermInfo,          // 含校历：开始/结束日期、总周数
    val campuses: List<CampusOption>,            // 网站顺序
    val defaultCampusId: String,                 // 网站默认校区
    val buildingsByCampus: Map<String, List<BuildingOption>>, // 至少含默认校区；其余可懒加载
    val weeks: List<WeekOption>,                 // 当前学期全部周次 + 可选性
    val periods: List<PeriodOption>,             // 默认校区的节次与真实时间
    val categories: List<VenueCategory>,
)
