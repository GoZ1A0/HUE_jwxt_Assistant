package com.hebeu.emptyclassroom.domain.model

import kotlinx.serialization.Serializable

/**
 * 一次「空闲教室」查询条件（UI -> 数据层）。不可变。
 *
 * 真实协议：`POST /cdjy/cdjy_cxKxcdlb.html?doType=query`（按周次 jyfs=0）。
 * 周次/星期/节次必须各选至少一项（与网页端校验一致）。
 */
data class ClassroomQuery(
    val term: TermOption,
    val campusId: String,
    val buildingId: String?,        // null = 全部
    val weeks: Set<Int>,            // 1..totalWeeks（显示周次），非空
    val weekdays: Set<Int>,         // 1=周一..7=周日，非空
    val periods: Set<Int>,          // 1..10 节（JCMC），非空
    val categoryId: String?,        // null = 全部
    val subCategoryId: String?,     // null = 全部
    val minSeats: Int?,             // qszws（座位数下限）
    val maxSeats: Int?,             // jszws（座位数上限）
    val roomName: String?,          // cdmc（场地名称/编号模糊搜索）
) {
    /**
     * 转成真实 POST body 的唯一入口（UI 不得自己拼 HTTP）。
     *
     * 参数与 `docs/recon/site-contract.md` §5.1 完全一致：
     * - `zcd` = Σ 2^(周-1)（周次位掩码）
     * - `xqj` = 逗号分隔的星期（周一=1）
     * - `jcd` = Σ 2^(节-1)（节次位掩码，按 JCMC）
     * - `queryModel.showCount` 服务端实测最大接受 500，这里限制在 1..500
     */
    fun toFormBody(page: Int, pageSize: Int): Map<String, String> {
        val safePage = if (page < 1) 1 else page
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val body = LinkedHashMap<String, String>(40)
        body["xqh_id"] = campusId
        body["xnm"] = term.xnm
        body["xqm"] = term.xqm
        body["cdlb_id"] = categoryId.orEmpty()
        body["cdejlb_id"] = subCategoryId.orEmpty()
        body["qszws"] = minSeats?.toString().orEmpty()
        body["jszws"] = maxSeats?.toString().orEmpty()
        body["cdmc"] = roomName.orEmpty()
        body["cd_id"] = ""
        body["lh"] = buildingId.orEmpty()
        body["jyfs"] = JYFS_BY_WEEK
        // 本部署前端照发空值的学校专用过滤列（见契约 §5.1 参数表）
        body["cdjylx"] = ""
        body["zysx"] = ""
        body["sflb"] = ""
        body["hbsl"] = ""
        body["bbsl"] = ""
        body["sfyzz"] = ""
        body["sfjtjs"] = ""
        body["tjsl"] = ""
        body["tymbsl"] = ""
        body["yczb"] = ""
        body["zws"] = ""
        body["sfbhkc"] = ""
        body["kszws1"] = ""
        body["zcd"] = bitMask(weeks.map { it - 1 })
        body["xqj"] = weekdays.filter { it in 1..7 }.sorted().joinToString(",")
        body["jcd"] = bitMask(periods.map { it - 1 })
        body["queryModel.showCount"] = safePageSize.toString()
        body["queryModel.currentPage"] = safePage.toString()
        body["queryModel.sortName"] = DEFAULT_SORT_NAME
        body["queryModel.sortOrder"] = DEFAULT_SORT_ORDER
        body["time"] = "0"
        return body
    }

    companion object {
        const val MAX_PAGE_SIZE: Int = 500
        const val DEFAULT_SORT_NAME: String = "cdbh"
        const val DEFAULT_SORT_ORDER: String = "asc"

        /** 学生端只有「按周次」一种查询方式（网页无按日期页签）。 */
        const val JYFS_BY_WEEK: String = "0"

        /**
         * 位掩码：Σ 2^bitIndex，bitIndex 从 0 起。
         * `bitMask(listOf(0)) == "1"`、`bitMask(listOf(0, 2)) == "5"`。
         */
        fun bitMask(bitIndexes: Collection<Int>): String {
            var mask = 0L
            for (index in bitIndexes) {
                if (index in 0..62) mask = mask or (1L shl index)
            }
            return mask.toString()
        }
    }
}

/** DataStore 持久化用的「上次查询条件」（不含任何凭据）。 */
@Serializable
data class SavedQuery(
    val termKey: String,
    val campusId: String,
    val buildingId: String? = null,
    val weeks: List<Int> = emptyList(),
    val weekdays: List<Int> = emptyList(),
    val periods: List<Int> = emptyList(),
    val categoryId: String? = null,
    val subCategoryId: String? = null,
    val minSeats: Int? = null,
    val maxSeats: Int? = null,
    val roomName: String? = null,
)
