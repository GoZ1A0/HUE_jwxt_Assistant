package com.hebeu.emptyclassroom.data.remote.parsers

import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.model.*
import kotlinx.serialization.json.*
import org.jsoup.Jsoup

object AcademicParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun options(html: String): AcademicOptions {
        val document = Jsoup.parse(html)
        val years = document.select("select#xnm option").filter { it.attr("value").isNotBlank() }
        val semesters = document.select("select#xqm option").filter { it.attr("value").isNotBlank() }
        val terms = years.flatMap { year -> semesters.map { semester ->
            AcademicTerm(year.attr("value"), semester.attr("value"), "${year.text()} · 第${semester.text()}学期")
        } }
        val year = document.selectFirst("#xnm option[selected]")?.attr("value")
        val semester = document.selectFirst("#xqm option[selected]")?.attr("value")
        val selected = terms.firstOrNull { it.year == year && it.semester == semester } ?: terms.firstOrNull()
            ?: throw AppError.Parse(IllegalStateException("学期选项为空"))
        return AcademicOptions(terms, selected)
    }

    fun schedule(raw: String, term: AcademicTerm, now: Long): ScheduleData {
        val root = json.parseToJsonElement(raw).jsonObject
        if (root["xnxqsfkz"]?.jsonPrimitive?.contentOrNull == "true") throw AppError.Server(null, "该学期课表尚未开放")
        if (root["kbList"] !is JsonArray) throw AppError.Parse(IllegalStateException("课表缺少 kbList"))
        val lessons = listOf("kbList", "sjkList", "jxhjkcList").flatMap { key ->
            (root[key] as? JsonArray).orEmpty().mapIndexed { index, element ->
                val row = element.jsonObject
                val weeks = row.text("zcd") ?: row.text("zcmc").orEmpty()
                val periods = row.text("jc").orEmpty()
                Lesson("$key-${row.text("jxb_id").orEmpty()}-$index", row.text("kcmc") ?: "未命名课程",
                    row.text("xm") ?: row.text("jsxm"), row.text("cdmc"), row.text("xqj")?.toIntOrNull(),
                    SchedulePatterns.periods(periods), SchedulePatterns.weeks(weeks), weeks, periods, row.text("xkbz"),
                    row.text("kch") ?: row.text("kch_id"), row.text("xf"), row.text("kcxzmc") ?: row.text("kclb"))
            }
        }
        return ScheduleData(term, lessons, now)
    }

    fun overview(html: String): Pair<String?, String?> {
        val doc = Jsoup.parse(html)
        fun value(name: String) = doc.selectFirst("a[name=$name]")?.nextElementSibling()?.text()
            ?.replace("\u00a0", " ")?.trim()?.toBigDecimalOrNull()?.toPlainString()
        if (doc.selectFirst("a[name=showGpa]") == null) throw AppError.Parse(IllegalStateException("学业页面缺少 GPA 字段"))
        return value("showGpa") to value("showJqpjf")
    }

    data class GradePage(val records: List<GradeRecord>, val pages: Int)
    fun grades(raw: String): GradePage {
        val root = json.parseToJsonElement(raw).jsonObject
        val items = root["items"] as? JsonArray ?: throw AppError.Parse(IllegalStateException("成绩缺少 items"))
        return GradePage(items.mapIndexed { index, element ->
            val row = element.jsonObject
            GradeRecord(row.text("row_id") ?: row.text("key") ?: "${row.text("kch")}-${row.text("xnm")}-${row.text("xqm")}-${row.text("ksxz")}-${index}",
                row.text("kcmc") ?: "未命名课程", listOfNotNull(row.text("xnmmc"), row.text("xqmmc")).joinToString(" · "),
                row.text("cj"), row.text("xf"), row.text("jd"), row.text("xfjd"), row.text("kcxzmc"), row.text("ksxz"),
                row.text("kch") ?: row.text("kch_id"), row.text("xnm"), row.text("xqm"))
        }, root.text("totalPage")?.toIntOrNull() ?: 1)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        ?.trim()?.takeUnless { it.isBlank() || it == "null" }
}
