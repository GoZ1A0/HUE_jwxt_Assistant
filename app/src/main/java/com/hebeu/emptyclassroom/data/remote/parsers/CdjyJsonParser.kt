package com.hebeu.emptyclassroom.data.remote.parsers

import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.PeriodOption
import com.hebeu.emptyclassroom.domain.model.TermInfo
import com.hebeu.emptyclassroom.domain.model.WeekOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 空闲教室模块 JSON 接口解析：
 * - `GET /cdjy/cdjy_cxXqjc.html` → 楼号 + 节次
 * - `POST /cdjy/cdjy_cxQtlb.html` → 周次表 + 校历配置
 * - `GET /query/query_cxEjjxcdlbList.html` → 二级类别（本部署恒空）
 */
object CdjyJsonParser {

    private val EMPTY_BUILDING = BuildingOption("", "全部")

    /** 周次表 + 校历。 */
    data class CalendarData(
        val weeks: List<WeekOption>,
        val termInfo: TermInfo,
    )

    /**
     * 解析 cxXqjc 响应：
     * `{"lhList":[{"XQH_ID","JXLDM","JXLMC"}...],"jcList":[{"RSDMC","SJD","JCMC","RSDZJS"}...]}`。
     *
     * 楼号列表会**前插 id="" 的「全部」项**（cxXqjc 自身不返回「全部」）。
     */
    fun parseCampusDetail(json: String, campusId: String): CampusDetail {
        val root = parseJsonObjectOrNull(json)
            ?: return CampusDetail(campusId, listOf(EMPTY_BUILDING), emptyList())

        val buildings = root.jsonArray("lhList").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.jsonText("JXLDM") ?: return@mapNotNull null
            BuildingOption(id = id, name = item.jsonText("JXLMC") ?: id)
        }.filter { it.id.isNotEmpty() }

        val periods = root.jsonArray("jcList").mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            PeriodOption(
                number = item.jsonInt("JCMC") ?: (index + 1),
                timeRange = item.jsonText("SJD"),
                partOfDay = item.jsonText("RSDMC"),
                periodsPerBlock = item.jsonInt("RSDZJS"),
            )
        }.sortedBy { it.number }

        return CampusDetail(
            campusId = campusId,
            buildings = listOf(EMPTY_BUILDING) + buildings,
            periods = periods,
        )
    }

    /**
     * 解析 cxQtlb 响应：
     * `{"nxqzcList":[{"zc","dxqzc","rqfw","zczt"}...],"dqzcxq":{"ZXRQ","ZDRQ","ZDZC","DQZC",...}}`。
     *
     * - `selectable = zczt != "0"`（契约：zczt=="0" 的周不可选）
     * - `bitValue = dxqzc`（zcd 位掩码用值，通常等于 week）
     * - `startDate = ZXRQ + (week-1)*7`（契约已验证的日期推导；不要用 rqfw 推日期）
     */
    fun parseCalendar(json: String, fallbackLabel: String, fallbackTotalWeeks: Int = 0): CalendarData {
        val root = parseJsonObjectOrNull(json)
            ?: return CalendarData(emptyList(), defaultTermInfo(fallbackLabel, fallbackTotalWeeks))

        val calendar = root.jsonObject("dqzcxq")
        val termStart = parseDate(calendar?.jsonText("ZXRQ"))
        val termEnd = parseDate(calendar?.jsonText("ZDRQ"))

        val weeks = parseWeeks(root.jsonArray("nxqzcList"), termStart)

        val totalWeeks = calendar?.jsonInt("ZDZC")
            ?: weeks.size.takeIf { it > 0 }
            ?: fallbackTotalWeeks

        val termInfo = TermInfo(
            label = fallbackLabel,
            totalWeeks = totalWeeks,
            currentWeek = calendar?.jsonInt("DQZC"),
            startDate = termStart,
            endDate = termEnd,
        )
        return CalendarData(weeks = weeks, termInfo = termInfo)
    }

    /** 二级类别（`[{CDLB_ID, CDLBMC}]`）；本部署恒返回 `[]`。 */
    fun parseSubCategories(json: String): List<BuildingOption> {
        val array = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray
            ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.jsonText("CDLB_ID") ?: item.jsonText("cdlb_id") ?: return@mapNotNull null
            BuildingOption(id = id, name = item.jsonText("CDLBMC") ?: item.jsonText("cdlbmc") ?: id)
        }
    }

    private fun parseWeeks(array: JsonArray, termStart: LocalDate?): List<WeekOption> =
        array.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val week = item.jsonInt("zc") ?: (index + 1)
            if (week <= 0) return@mapIndexedNotNull null
            WeekOption(
                week = week,
                bitValue = item.jsonInt("dxqzc") ?: week,
                dateRangeText = item.jsonText("rqfw"),
                startDate = termStart?.plusWeeks((week - 1).toLong()),
                selectable = item.jsonText("zczt") != "0",
            )
        }.sortedBy { it.week }

    private fun defaultTermInfo(label: String, totalWeeks: Int): TermInfo = TermInfo(
        label = label,
        totalWeeks = totalWeeks,
        currentWeek = null,
        startDate = null,
        endDate = null,
    )

    private fun parseDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalDate.parse(text.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
