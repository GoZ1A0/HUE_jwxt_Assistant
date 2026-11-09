package com.hebeu.emptyclassroom.domain.model

/** 一条空闲教室结果（真实响应 items[] 的一行，全部字段为字符串，缺失字段直接省略）。 */
data class Classroom(
    val id: String,                 // cd_id
    val code: String?,              // cdbh（场地编号）
    val name: String,               // cdmc（场地名称，列表主标题）
    val campusName: String?,        // xqmc
    val buildingName: String?,      // jxlmc
    val categoryName: String?,      // cdlbmc
    val seats: Int?,                // zws（座位数）
    val examSeats: Int?,            // kszws1（考试座位数，可能缺失）
    val department: String?,        // sydxmc（使用部门，可能缺失）
    val periodText: String?,        // cdjc（场地简称，可能缺失；不是节次）
)

/** 一页查询结果。空结果是 `items.isEmpty() && total == 0`，不是错误。 */
data class QueryResult(
    val items: List<Classroom>,
    val total: Int,                 // totalResult
    val page: Int,                  // currentPage
    val totalPages: Int,            // totalPage
    val queriedAt: Long,            // 本地成功响应时间 System.currentTimeMillis()
) {
    val isEmpty: Boolean get() = items.isEmpty() && total == 0
    val hasMore: Boolean get() = page < totalPages
}
