package com.quanquan.quantoot.data

import kotlinx.serialization.json.*

internal const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
internal val apiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

internal fun boundedJson(text: String): JsonElement {
    if (text.length > MAX_RESPONSE_BYTES) throw ApiException(-1, "Response exceeds the 8 MiB limit")
    var depth = 0
    var tokens = 0
    var quoted = false
    var escaped = false
    var primitive = false
    for (char in text) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true else if (char == '"') quoted = false
            continue
        }
        when (char) {
            '"' -> {
                quoted = true
                tokens++
                primitive = false
            }
            '{',
            '[' -> {
                depth++
                tokens++
                primitive = false
                if (depth > 64) throw ApiException(-1, "JSON nesting exceeds the limit")
            }
            '}',
            ']' -> {
                depth--
                primitive = false
            }
            ',',
            ':',
            ' ',
            '\r',
            '\n',
            '\t' -> primitive = false
            else ->
                if (!primitive) {
                    primitive = true
                    tokens++
                }
        }
        if (tokens > 100_000) throw ApiException(-1, "JSON token count exceeds the limit")
    }
    return try {
        apiJson.parseToJsonElement(text)
    } catch (_: IllegalArgumentException) {
        throw ApiException(-1, "Invalid JSON response")
    }
}

internal fun normalizeStatuses(element: JsonElement, statusDepth: Int = 0): JsonElement =
    when (element) {
        is JsonArray -> JsonArray(element.map { normalizeStatuses(it, statusDepth) })
        is JsonObject -> {
            val isStatus =
                "content" in element && "account" in element ||
                    "reblog" in element ||
                    "media_attachments" in element
            val depth = statusDepth + if (isStatus) 1 else 0
            JsonObject(
                element.mapValues { (key, value) ->
                    if (depth >= 6 && key in setOf("reblog", "quote", "quoted_status")) JsonNull
                    else normalizeStatuses(value, depth)
                }
            )
        }
        else -> element
    }

internal inline fun <reified T> decodeApi(element: JsonElement): T =
    try {
        apiJson.decodeFromJsonElement<T>(normalizeStatuses(element))
    } catch (_: IllegalArgumentException) {
        throw ApiException(-1, "Unexpected server response")
    }
