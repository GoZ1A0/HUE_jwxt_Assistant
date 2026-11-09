package com.hebeu.emptyclassroom.data.remote.parsers

import com.hebeu.emptyclassroom.domain.model.Classroom
import com.hebeu.emptyclassroom.domain.model.QueryResult
import kotlinx.serialization.json.JsonObject

/**
 * 空闲教室查询响应解析（`POST /cdjy/cdjy_cxKxcdlb.html?doType=query`）。
 *
 * 真实响应是 jqGrid 包装的 JSON（契约 §5.2）：
 * ```json
 * {"currentPage":1,"totalPage":4,"totalResult":52,"showCount":15,"items":[{...}]}
 * ```
 * - 空结果：`items=[]`、`totalResult=0`、`totalPage=0`（是正常结果，不是错误）
 * - 单行**所有字段都是字符串**，缺失字段直接省略
 * - 非 JSON（HTML 错误页）由调用方先判定，本解析器只处理 JSON
 */
object QueryResultParser {

    fun parse(json: String, fallbackPage: Int, queriedAt: Long = System.currentTimeMillis()): QueryResult {
        val root = parseJsonObjectOrNull(json)
            ?: throw IllegalArgumentException("query response is not a JSON object")

        val items = root.jsonArray("items").mapIndexedNotNull { index, element ->
            (element as? JsonObject)?.let { toClassroom(it, index) }
        }
        val total = root.jsonInt("totalResult") ?: root.jsonInt("totalCount") ?: items.size
        val page = root.jsonInt("currentPage") ?: fallbackPage
        val totalPages = root.jsonInt("totalPage") ?: 0

        return QueryResult(
            items = items,
            total = total,
            page = page,
            totalPages = totalPages,
            queriedAt = queriedAt,
        )
    }

    /** 单个 item；`cd_id`/`cdmc` 缺失时用编号或行号兜底，保证 UI 列表 key 非空唯一。 */
    private fun toClassroom(item: JsonObject, index: Int): Classroom {
        val code = item.jsonText("cdbh")
        val id = item.jsonText("cd_id") ?: code ?: "row_$index"
        val name = item.jsonText("cdmc") ?: code ?: "未命名教室"
        return Classroom(
            id = id,
            code = code,
            name = name,
            campusName = item.jsonText("xqmc"),
            buildingName = item.jsonText("jxlmc"),
            categoryName = item.jsonText("cdlbmc"),
            seats = item.jsonInt("zws"),
            examSeats = item.jsonInt("kszws1"),
            department = item.jsonText("sydxmc"),
            periodText = item.jsonText("cdjc"),
        )
    }
}
