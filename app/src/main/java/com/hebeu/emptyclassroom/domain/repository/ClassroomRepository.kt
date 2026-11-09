package com.hebeu.emptyclassroom.domain.repository

import com.hebeu.emptyclassroom.domain.error.AppError
import com.hebeu.emptyclassroom.domain.error.AppError.TermClosed
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.ClassroomQuery
import com.hebeu.emptyclassroom.domain.model.QueryOptions
import com.hebeu.emptyclassroom.domain.model.QueryResult
import com.hebeu.emptyclassroom.domain.model.TermOption

/**
 * 空闲教室数据仓库（只读）。所有方法失败时抛 [AppError]；会话失效时同时把
 * `AuthRepository.session` 置为 Expired。
 */
interface ClassroomRepository {

    /**
     * 每次登录后 / 手动刷新时调用：GET 空闲教室页面并解析选项与校历，再请求默认校区的楼号与节次。
     * 失败抛 [AppError]。
     */
    suspend fun loadOptions(): QueryOptions

    /**
     * 切换校区时按需加载楼号与节次（`GET /cdjy/cdjy_cxXqjc.html`）；UI 按 campusId 缓存。
     */
    suspend fun loadCampusDetail(campusId: String): CampusDetail

    /**
     * 查询前预检某学期场地预约是否开放（`POST /cdjy/cdjy_cxSfkfyuy.html`）。
     * @return true=开放；false=未开放（查询会抛 [TermClosed]）
     */
    suspend fun checkTermOpen(term: TermOption): Boolean

    /**
     * 真实查询；page 从 1 开始。首页会先做学期开放预检（未开放抛 [TermClosed]）。
     * 分页数据以真实返回为准；空结果不是错误。
     */
    suspend fun query(query: ClassroomQuery, page: Int, pageSize: Int = 100): QueryResult
}
