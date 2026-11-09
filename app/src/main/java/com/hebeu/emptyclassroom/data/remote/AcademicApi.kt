package com.hebeu.emptyclassroom.data.remote

import com.hebeu.emptyclassroom.data.remote.parsers.AcademicParser
import com.hebeu.emptyclassroom.data.remote.parsers.AcademicEventParser
import com.hebeu.emptyclassroom.data.remote.parsers.ErrorPageDetector
import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.model.*

/** 路由和参数来自本校登录后页面及其脚本，见 docs/recon/academic-contract.md。 */
class AcademicApi(private val http: ZfHttpClient) {
    suspend fun options(): AcademicOptions = AcademicParser.options(page(SCHEDULE_PAGE, "N2151"))
    suspend fun schedule(term: AcademicTerm): ScheduleData = AcademicParser.schedule(post(
        "kbcx/xskbcx_cxXsgrkb.html", "N2151", mapOf("xnm" to term.year, "xqm" to term.semester,
            "kzlx" to "ck", "xsdm" to "", "kclbdm" to "", "kclxdm" to "")), term, System.currentTimeMillis())

    suspend fun overview(): Pair<String?, String?> = AcademicParser.overview(page(
        "xsxy/xsxyqk_cxXsxyqkIndex.html", "N105515"))

    suspend fun exams(term: AcademicTerm): List<ExamInfo> {
        val all = mutableListOf<ExamInfo>(); var number = 1
        do {
            val page = AcademicEventParser.exams(post("kwgl/kscx_cxXsksxxIndex.html", "N358105", mapOf(
                "xnm" to term.year, "xqm" to term.semester, "ksmcdmb_id" to "", "kch" to "", "ksrq" to "", "ksxz" to "",
                "queryModel.currentPage" to number.toString(), "queryModel.showCount" to "500", "queryModel.sortName" to "", "queryModel.sortOrder" to "asc"), true), term)
            all += page.items; number++
        } while (number <= page.pages)
        return all.distinctBy { it.id }
    }

    suspend fun notices(): List<SchoolNotice> {
        val all = mutableListOf<SchoolNotice>()
        for (read in listOf("1", "2")) {
            var number = 1
            do {
                val page = AcademicEventParser.notices(post("xtgl/index_cxDbsy.html", "", mapOf("flag" to "1", "sfyy" to read,
                    "queryModel.currentPage" to number.toString(), "queryModel.showCount" to "500", "queryModel.sortName" to "cjsj", "queryModel.sortOrder" to "desc"), true))
                all += page.items; number++
            } while (number <= page.pages)
        }
        return all.distinctBy { it.key }.sortedByDescending { it.createdAt }
    }

    suspend fun grades(term: AcademicTerm?): List<GradeRecord> {
        val all = mutableListOf<GradeRecord>()
        var page = 1
        do {
            val result = AcademicParser.grades(post("cjcx/cjcx_cxXsgrcj.html", "N305005", mapOf(
                "xnm" to term?.year.orEmpty(), "xqm" to term?.semester.orEmpty(), "sfzgcj" to "", "kcbj" to "",
                "queryModel.currentPage" to page.toString(), "queryModel.showCount" to "500",
                "queryModel.sortName" to "", "queryModel.sortOrder" to "asc", "time" to "0"), query = true))
            all += result.records
            page++
        } while (page <= result.pages)
        return all
    }

    private suspend fun page(path: String, menu: String): String {
        val result = http.get(http.url(path, mapOf("gnmkdm" to menu, "layout" to "default")))
        checkResult(result)
        if (ErrorPageDetector.looksLikeErrorPage(result.body)) throw AppError.Server(result.code, "学校页面暂时不可用")
        return result.body
    }

    private suspend fun post(path: String, menu: String, form: Map<String, String>, query: Boolean = false): String {
        val result = http.postForm(http.url(path, buildMap {
            put("gnmkdm", menu); if (query) put("doType", "query")
        }), form, mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to http.url(SCHEDULE_PAGE).toString()))
        checkResult(result)
        if (!result.isJson) throw AppError.Parse(IllegalStateException("预期 JSON 响应"))
        return result.body
    }

    private fun checkResult(result: HttpResult) {
        if (ZfHttpClient.isSessionExpired(result) || result.looksLikeLoginPage) throw AppError.SessionExpired()
        if (!result.isSuccessful) throw AppError.Server(result.code)
    }
    companion object { const val SCHEDULE_PAGE = "kbcx/xskbcx_cxXskbcxIndex.html" }
}
