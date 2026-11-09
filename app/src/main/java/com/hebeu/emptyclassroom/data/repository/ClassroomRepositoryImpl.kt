package com.hebeu.emptyclassroom.data.repository

import com.hebeu.emptyclassroom.data.remote.CdjyApi
import com.hebeu.emptyclassroom.data.remote.parsers.OptionsPageParser
import com.hebeu.emptyclassroom.data.session.SessionStateHolder
import com.hebeu.emptyclassroom.data.session.SessionGuard
import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.ClassroomQuery
import com.hebeu.emptyclassroom.domain.model.QueryOptions
import com.hebeu.emptyclassroom.domain.model.QueryResult
import com.hebeu.emptyclassroom.domain.error.AppError.TermClosed
import com.hebeu.emptyclassroom.domain.model.TermOption
import com.hebeu.emptyclassroom.domain.repository.ClassroomRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 空闲教室数据仓库实现。
 *
 * 真实请求序列（契约 §4/§5）：
 * ```
 * loadOptions():  GET 页面(解析学期/校区/类别) -> POST cxQtlb(周次+校历) -> GET cxXqjc(默认校区楼号+节次)
 * loadCampusDetail(): GET cxXqjc(指定校区)
 * query():        POST cxSfkfyuy(首页预检) -> POST cxKxcdlb?doType=query
 * ```
 *
 * 会话失效（901 / 302 登录页）→ 把共享 [SessionStateHolder] 置 Expired，再抛 [AppError.SessionExpired]。
 */
class ClassroomRepositoryImpl(
    private val api: CdjyApi,
    private val sessionHolder: SessionStateHolder,
    private val guard: SessionGuard? = null,
) : ClassroomRepository {

    /** 记住最近一次 loadOptions 的学期/默认校区，供 loadCampusDetail 使用。 */
    @Volatile
    private var cachedContext: OptionsContext? = null

    override suspend fun loadOptions(): QueryOptions = withContext(Dispatchers.IO) {
        guarded {
            val html = api.fetchOptionsPageHtml()
            val page = OptionsPageParser.parse(html)

            val term = page.terms.firstOrNull { it.key == page.selectedTermKey }
                ?: page.terms.firstOrNull { termOption ->
                    termOption.xnm == page.hiddenXnm && termOption.xqm == page.hiddenXqm
                }
                ?: page.terms.firstOrNull()
                ?: throw AppError.Parse(IllegalStateException("options page has no term"))

            val campusId = page.defaultCampusId
                ?: page.campuses.firstOrNull()?.id
                ?: throw AppError.Parse(IllegalStateException("options page has no campus"))

            val calendar = api.fetchCalendar(term, campusId, page.isCdjysqflg)
            val campusDetail = api.fetchCampusDetail(term, campusId)

            cachedContext = OptionsContext(term = term, campusId = campusId)
            QueryOptions(
                terms = page.terms,
                selectedTermKey = term.key,
                termInfo = calendar.termInfo,
                campuses = page.campuses,
                defaultCampusId = campusId,
                buildingsByCampus = mapOf(campusId to campusDetail.buildings),
                weeks = calendar.weeks,
                periods = campusDetail.periods,
                categories = page.categories,
            )
        }
    }

    override suspend fun loadCampusDetail(campusId: String): CampusDetail = withContext(Dispatchers.IO) {
        guarded {
            val context = cachedContext ?: run {
                loadOptions()
                cachedContext
            } ?: throw AppError.Parse(IllegalStateException("options not loaded"))
            api.fetchCampusDetail(context.term, campusId)
        }
    }

    override suspend fun checkTermOpen(term: TermOption): Boolean = withContext(Dispatchers.IO) {
        guarded { api.checkTermOpen(term) }
    }

    override suspend fun query(query: ClassroomQuery, page: Int, pageSize: Int): QueryResult =
        withContext(Dispatchers.IO) {
            guarded {
                // 契约：「查询前应先调用」学期场地预约开放预检；只在首页做一次，避免翻页重复请求
                if (page <= 1 && !api.checkTermOpen(query.term)) {
                    throw TermClosed("该学年学期场地预约尚未开放申请，请联系相关管理人员")
                }
                api.query(query, page, pageSize)
            }
        }

    /** 统一处理会话失效：同步把 AuthRepository.session 置为 Expired。 */
    private suspend fun <T> guarded(block: suspend () -> T): T = if (guard != null) guard.run(block) else try {
        block()
    } catch (e: AppError.SessionExpired) {
        sessionHolder.onExpired(SESSION_EXPIRED_MESSAGE)
        throw e
    }

    private data class OptionsContext(val term: TermOption, val campusId: String)

    private companion object {
        const val SESSION_EXPIRED_MESSAGE = "登录已过期，请重新登录"
    }
}
