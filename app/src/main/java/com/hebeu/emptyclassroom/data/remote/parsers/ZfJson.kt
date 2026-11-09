package com.hebeu.emptyclassroom.data.remote.parsers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 解析 ZFsoft JSON 的小工具（内部使用）。
 *
 * 真实响应特点（契约 §5.2）：字段值**全部是字符串**（`"zws":"100"`），
 * 缺失字段被直接省略（不是 null），顶层还有大量 jqGrid 噪声字段。
 * 因此所有读取都必须是宽松的、可缺省的。
 */
internal fun parseJsonObjectOrNull(json: String): JsonObject? =
    runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject

/** 字符串字段；缺失、null、空白都返回 null。 */
internal fun JsonObject.jsonText(key: String): String? {
    val element: JsonElement = this[key] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

/** 数字字段（真实响应多为字符串数字，如 `"zws":"100"`）。 */
internal fun JsonObject.jsonInt(key: String): Int? = jsonText(key)?.toIntOrNull()

internal fun JsonObject.jsonObject(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.jsonArray(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
