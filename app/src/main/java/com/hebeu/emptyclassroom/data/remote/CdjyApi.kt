package com.hebeu.emptyclassroom.data.remote

import com.hebeu.emptyclassroom.data.remote.parsers.CdjyJsonParser
import com.hebeu.emptyclassroom.data.remote.parsers.ErrorPageDetector
import com.hebeu.emptyclassroom.data.remote.parsers.QueryResultParser
import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.ClassroomQuery
import com.hebeu.emptyclassroom.domain.model.QueryResult
import com.hebeu.emptyclassroom.domain.model.TermOption

/**
 * 空闲教室（cdjy）模块 API。所有响应都会先做会话失效判定：
 * HTTP **901**（带 `X-Requested-With` 的 XHR）或 **302 到登录页** → [AppError.SessionExpired]。
 *
 * 接口清单（docs/recon/site-contract.md §3/§4/§5）：
 * - `GET  cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155`        页面（选项来源）
 * - `GET  cdjy/cdjy_cxXqjc.html?xqh_id&xnm&xqm`         楼号 + 节次（切校区必调）
 * - `POST cdjy/cdjy_cxQtlb.html`                       周次表 + 校历（flag=isCdjysqflg）
 * - `POST cdjy/cdjy_cxSfkfyuy.html`                    学期预约是否开放（"1"/"0"）
 * - `POST cdjy/cdjy_cxKxcdlb.html?doType=query`        查询（JSON）
 * - `GET  query/query_cxEjjxcdlbList.html`             二级类别（本部署恒 []）
 */
class CdjyApi(
    private val http: ZfHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** GET 页面 HTML；会话失效/服务端异常会抛 AppError。 */
    suspend fun fetchOptionsPageHtml(): String {
        val result = http.get(
            url = pageUrl(),
            headers = mapOf("Referer" to http.baseUrl.toString() + "xtgl/index_initMenu.html"),
        )
        ensureSession(result)
        if (!result.isSuccessful) throw AppError.Server(result.code, result.bodySnippet())
        if (result.body.isBlank()) throw AppError.Parse(IllegalStateException("empty options page"))
        if (ErrorPageDetector.looksLikeErrorPage(result.body)) {
            throw AppError.Server(result.code, "系统维护页面")
        }
        return result.body
    }

    /** cxXqjc：某个校区的楼号 + 节次（切换校区时必须重新请求）。 */
    suspend fun fetchCampusDetail(term: TermOption, campusId: String): CampusDetail {
        val result = http.get(
            url = http.url(
                CAMPUS_DETAIL_PATH,
                mapOf("xqh_id" to campusId, "xnm" to term.xnm, "xqm" to term.xqm),
            ),
            headers = xhrHeaders(),
        )
        ensureSession(result)
        ensureJson(result, "cxXqjc")
        return CdjyJsonParser.parseCampusDetail(result.body, campusId)
    }

    /** cxQtlb：周次表 + 校历配置。flag 取页面内联变量 isCdjysqflg（本部署为 "0"）。 */
    suspend fun fetchCalendar(term: TermOption, campusId: String, flag: String?): CdjyJsonParser.CalendarData {
        val result = http.postForm(
            url = http.url(CALENDAR_PATH),
            form = linkedMapOf(
                "xnm" to term.xnm,
                "xqm" to term.xqm,
                "xqh_id" to campusId,
                "flag" to (flag ?: DEFAULT_FLAG),
            ),
            headers = xhrHeaders(),
        )
        ensureSession(result)
        ensureJson(result, "cxQtlb")
        return CdjyJsonParser.parseCalendar(result.body, term.label)
    }

    /**
     * cxSfkfyuy：学期场地预约是否开放。
     *
     * 真实站点实测（2026-11-07，QA E2E）：HTTP 200 + `Content-Type: application/json;charset=UTF-8`
     * + body 为 **JSON 字符串 `"1"`（带双引号，3 字节）**；部分部署/网关也可能返回裸文本 `1`/`0`，
     * 两种形态都兼容。
     *
     * @return true=开放（"1"），false=未开放（"0"）
     */
    suspend fun checkTermOpen(term: TermOption): Boolean {
        val result = http.postForm(
            url = http.url(TERM_OPEN_PATH),
            form = linkedMapOf("xnm" to term.xnm, "xqm" to term.xqm),
            headers = xhrHeaders(),
        )
        ensureSession(result)
        if (result.code in 500..599) throw AppError.Server(result.code, result.bodySnippet())
        if (!result.isSuccessful) throw AppError.Server(result.code, result.bodySnippet())
        // 兼容 JSON 字符串（"1"）与裸文本（1），并容忍首尾空白
        val text = result.body.trim().removeSurrounding("\"").trim()
        return when (text) {
            "1" -> true
            "0" -> false
            else -> throw AppError.Parse(IllegalStateException("unexpected term-open response"))
        }
    }

    /** 真实查询；返回 JSON 解析后的结果。空结果不是错误。 */
    suspend fun query(query: ClassroomQuery, page: Int, pageSize: Int): QueryResult {
        val result = http.postForm(
            url = http.url(QUERY_PATH, mapOf("doType" to "query")),
            form = query.toFormBody(page, pageSize),
            headers = xhrHeaders(),
        )
        ensureSession(result)
        if (result.code in 500..599) throw AppError.Server(result.code, result.bodySnippet())
        ensureJson(result, "query")
        return try {
            QueryResultParser.parse(result.body, fallbackPage = page, queriedAt = clock())
        } catch (e: Exception) {
            throw AppError.Parse(e)
        }
    }

    /** 场地二级类别；本部署对所有类别都返回 `[]`。 */
    suspend fun fetchSubCategories(categoryId: String): List<BuildingOption> {
        val result = http.get(
            url = http.url(SUB_CATEGORY_PATH, mapOf("sscdlb_id" to categoryId, "cdyysfkj" to "1")),
            headers = xhrHeaders(),
        )
        ensureSession(result)
        ensureJson(result, "cdejlb")
        return CdjyJsonParser.parseSubCategories(result.body)
    }

    private fun pageUrl() = http.url(OPTIONS_PAGE_PATH, mapOf("gnmkdm" to GNMKDM))

    /** 会话失效统一判定：901 或重定向到登录页。 */
    private fun ensureSession(result: HttpResult) {
        if (result.code == ZfHttpClient.HTTP_SESSION_EXPIRED || result.redirectedToLogin) {
            throw AppError.SessionExpired()
        }
    }

    private fun ensureJson(result: HttpResult, apiName: String) {
        if (!result.isJson) {
            if (ErrorPageDetector.looksLikeErrorPage(result.body)) {
                throw AppError.Parse(IllegalStateException("$apiName returned an HTML error page"))
            }
            throw AppError.Parse(IllegalStateException("$apiName returned non-JSON response"))
        }
    }

    /** 与浏览器一致的 XHR 头；会话过期时服务端会返回 901（便于统一识别）。 */
    private fun xhrHeaders(): Map<String, String> = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Referer" to pageUrl().toString(),
        "Origin" to "${http.baseUrl.scheme}://${http.baseUrl.host}",
    )

    private companion object {
        const val GNMKDM = "N2155"
        const val DEFAULT_FLAG = "0"

        const val OPTIONS_PAGE_PATH = "cdjy/cdjy_cxKxcdlb.html"
        const val QUERY_PATH = "cdjy/cdjy_cxKxcdlb.html"
        const val CAMPUS_DETAIL_PATH = "cdjy/cdjy_cxXqjc.html"
        const val CALENDAR_PATH = "cdjy/cdjy_cxQtlb.html"
        const val TERM_OPEN_PATH = "cdjy/cdjy_cxSfkfyuy.html"
        const val SUB_CATEGORY_PATH = "query/query_cxEjjxcdlbList.html"
    }
}
