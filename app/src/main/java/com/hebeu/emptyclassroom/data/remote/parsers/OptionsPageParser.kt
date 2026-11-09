package com.hebeu.emptyclassroom.data.remote.parsers

import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.domain.model.CampusOption
import com.hebeu.emptyclassroom.domain.model.TermOption
import com.hebeu.emptyclassroom.domain.model.VenueCategory
import org.jsoup.Jsoup

/** 「查询空闲教室」页面 `/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155` 的解析结果。 */
data class OptionsPageData(
    val terms: List<TermOption>,
    val selectedTermKey: String?,
    val hiddenXnm: String?,
    val hiddenXqm: String?,
    val campuses: List<CampusOption>,
    val defaultCampusId: String?,
    val categories: List<VenueCategory>,
    /** 页面初始渲染的楼号（只有默认校区；切换校区必须走 cxXqjc）。 */
    val defaultBuildings: List<BuildingOption>,
    /** 页面内联变量 isCdjysqflg（POST cxQtlb 的 flag 取它）。 */
    val isCdjysqflg: String?,
)

/**
 * 空闲教室查询页 HTML 解析。
 *
 * 为什么解析页面而不是硬编码：学期（57 项）、校区、场地类别都是服务端模板渲染，
 * 会随学期/部署变化（契约 Q9）。
 */
object OptionsPageParser {

    private val EMPTY_BUILDING = BuildingOption("", "全部")

    fun parse(html: String): OptionsPageData {
        val document = Jsoup.parse(html)

        val terms = document.select("select#dm_cx option").mapNotNull { option ->
            val key = option.attr("value").trim()
            if (key.isEmpty()) return@mapNotNull null
            val parts = key.split('-')
            if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return@mapNotNull null
            TermOption(key = key, xnm = parts[0], xqm = parts[1], label = option.text().trim())
        }

        val selectedTermKey = document.selectFirst("select#dm_cx option[selected]")
            ?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }

        val hiddenXnm = document.selectFirst("input[name=xnm]")?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }
        val hiddenXqm = document.selectFirst("input[name=xqm]")?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }

        val campuses = document.select("select#xqh_id option").mapNotNull { option ->
            val id = option.attr("value").trim()
            if (id.isEmpty()) return@mapNotNull null
            CampusOption(id = id, name = option.text().trim())
        }
        val defaultCampusId = document.selectFirst("select#xqh_id option[selected]")
            ?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }
            ?: campuses.firstOrNull()?.id

        val categories = document.select("select#cdlb_id option").map { option ->
            VenueCategory(id = option.attr("value").trim(), name = option.text().trim())
        }

        val buildings = document.select("select#lh option").map { option ->
            BuildingOption(
                id = option.attr("value").trim(),
                name = option.text().trim().ifEmpty { "全部" },
            )
        }
        val defaultBuildings = if (buildings.any { it.id.isEmpty() }) buildings else listOf(EMPTY_BUILDING) + buildings

        return OptionsPageData(
            terms = terms,
            selectedTermKey = selectedTermKey,
            hiddenXnm = hiddenXnm,
            hiddenXqm = hiddenXqm,
            campuses = campuses,
            defaultCampusId = defaultCampusId,
            categories = categories,
            defaultBuildings = defaultBuildings,
            isCdjysqflg = parseIsCdjysqflg(html),
        )
    }

    /**
     * 页面内联：`var isCdjysqflg = $.founded('0')?'0':'';`
     * 该值作为 `POST /cdjy/cdjy_cxQtlb.html` 的 flag。
     */
    fun parseIsCdjysqflg(html: String): String? {
        val pattern = Regex("""isCdjysqflg\s*=\s*\$\.founded\(\s*'([^']*)'\s*\)""")
        pattern.find(html)?.groupValues?.getOrNull(1)?.let { return it }
        val fallback = Regex("""isCdjysqflg\s*=\s*['"]([^'"]*)['"]""")
        return fallback.find(html)?.groupValues?.getOrNull(1)
    }
}
