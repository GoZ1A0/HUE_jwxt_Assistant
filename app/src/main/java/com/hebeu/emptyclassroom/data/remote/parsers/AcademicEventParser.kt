package com.hebeu.emptyclassroom.data.remote.parsers

import com.hebeu.emptyclassroom.domain.model.*
import com.hebeu.emptyclassroom.domain.error.AppError
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalTime

object AcademicEventParser {
    data class Page<T>(val items: List<T>, val pages: Int)
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeUnless { it.isEmpty() || it == "null" }
    private fun rows(raw: String): Pair<JsonArray, Int> {
        val root = Json.parseToJsonElement(raw).jsonObject
        val items = root["items"] as? JsonArray ?: throw AppError.Parse(IllegalStateException("教务列表缺少 items"))
        return items to ((root["totalPage"] as? JsonPrimitive)?.intOrNull ?: 1)
    }
    private fun plain(value: String) = Jsoup.parse(value.replace(Regex("(?i)<br\\s*/?>"), "\n")).wholeText().trim()
    fun notices(raw: String): Page<SchoolNotice> {
        val (items, pages) = rows(raw)
        return Page(items.mapNotNull { e ->
            val r = e.jsonObject
            val title = plain(r.text("xxbt").orEmpty()); val body = plain(r.text("xxnr").orEmpty())
            if (listOf("调课", "停课", "补课").none { it in title || it in body }) null
            else SchoolNotice(r.text("zjxx") ?: r.text("id") ?: r.text("row_id") ?: academicHash(title + body), title, body, r.text("cjsj").orEmpty())
        }, pages)
    }
    fun exams(raw: String, term: AcademicTerm): Page<ExamInfo> {
        val (items, pages) = rows(raw)
        return Page(items.map { e ->
            val r = e.jsonObject
            val text = r.text("kssj").orEmpty()
            val normalized = text.replace('年','-').replace('月','-').replace("日", "").replace('：', ':')
            val date = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").find(r.text("ksrq") ?: normalized)?.let { m ->
                runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toString() }.getOrNull()
            }
            val times = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)").findAll(normalized).mapNotNull { m ->
                runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()).toString() }.getOrNull()
            }.toList()
            val start = times.getOrNull(0); val end = times.getOrNull(1)?.takeIf { start != null && it > start }
            val code = r.text("kch") ?: r.text("kch_id")
            ExamInfo(r.text("row_id") ?: academicHash("${term.key}|$code|${r.text("kcmc")}|${r.text("ksmc")}|$text"),
                r.text("kcmc") ?: "未命名考试", code, term, text.ifBlank { r.text("ksrq") ?: "时间待公布" }, date, start, end,
                r.text("cdmc") ?: r.text("jxdd"), r.text("zwh"), r.text("ksmc") ?: r.text("ksfs"), r.text("ksbz") ?: r.text("bzxx"))
        }, pages)
    }
}
