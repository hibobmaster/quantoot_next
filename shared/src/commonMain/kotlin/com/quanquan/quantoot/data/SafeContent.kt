package com.quanquan.quantoot.data

import com.quanquan.quantoot.platform.secureRandomBytes
import io.ktor.http.Url

class ApiException(val statusCode: Int, override val message: String) : Exception(message)

fun normalizeInstance(input: String): String {
    val value = input.trim()
    require(value.isNotEmpty() && value.none { it.isWhitespace() || it.code < 32 || it == '\\' }) {
        "Enter a valid HTTPS instance"
    }
    val candidate = if ("://" in value) value else "https://$value"
    require(candidate.startsWith("https://", ignoreCase = true)) { "An instance must use HTTPS" }
    val authority =
        candidate
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    require(validAuthority(authority)) { "Invalid instance address" }
    val url =
        try {
            Url(candidate)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid instance address")
        }
    require(url.encodedPath in listOf("", "/") && '?' !in candidate && '#' !in candidate) {
        "Enter only the instance hostname"
    }
    return authority.lowercase().removeSuffix(":443")
}

private fun validAuthority(authority: String): Boolean {
    if (authority.isBlank() || authority.any { it.code !in 33..126 || it in "@%\\?#" }) return false
    val host: String
    val port: String?
    if (authority.startsWith('[')) {
        val end = authority.indexOf(']')
        if (end < 2) return false
        host = authority.substring(1, end)
        if (!validIpv6(host)) return false
        val rest = authority.substring(end + 1)
        if (rest.isNotEmpty() && !rest.startsWith(':')) return false
        port = rest.removePrefix(":").takeIf { rest.isNotEmpty() }
    } else {
        if (authority.count { it == ':' } > 1) return false
        host = authority.substringBefore(':')
        port = if (':' in authority) authority.substringAfter(':') else null
        if (
            host.length > 253 ||
                host.split('.').any { label ->
                    label.isEmpty() ||
                        label.length > 63 ||
                        !label.first().isLetterOrDigit() ||
                        !label.last().isLetterOrDigit() ||
                        label.any { !it.isLetterOrDigit() && it != '-' }
                }
        )
            return false
    }
    return port == null || (port.all(Char::isDigit) && port.toIntOrNull() in 1..65535)
}

private fun validIpv6(host: String): Boolean {
    if (":::" in host) return false
    val pieces = host.split("::")
    if (pieces.size > 2) return false
    val groups = pieces.flatMap { it.split(':').filter(String::isNotEmpty) }
    var count = 0
    for ((index, group) in groups.withIndex()) {
        if ('.' in group) {
            val octets = group.split('.')
            if (
                index != groups.lastIndex ||
                    octets.size != 4 ||
                    octets.any {
                        it.isEmpty() || it.any { c -> !c.isDigit() } || it.toIntOrNull() !in 0..255
                    }
            )
                return false
            count += 2
        } else {
            if (group.length !in 1..4 || group.any { it !in "0123456789abcdefABCDEF" }) return false
            count++
        }
    }
    return if (pieces.size == 2) count < 8
    else count == 8 && !host.startsWith(':') && !host.endsWith(':')
}

fun safeWebUrl(input: String): String? {
    if (
        input.isBlank() ||
            input != input.trim() ||
            input.any { it.isWhitespace() || it.code < 32 || it == '\\' }
    )
        return null
    val scheme = input.substringBefore("://", "").lowercase()
    if (scheme != "https" && scheme != "http") return null
    val authority =
        input.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (!validAuthority(authority)) return null
    return try {
        Url(input)
        input
    } catch (_: Exception) {
        null
    }
}

fun randomOperationId(): String =
    secureRandomBytes(24).joinToString("") { byte ->
        (byte.toInt() and 255).toString(16).padStart(2, '0')
    }

private fun decodeEntities(text: String): String =
    Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z]{2,12});").replace(text) { match ->
        val entity = match.groupValues[1]
        val number =
            when {
                entity.startsWith("#x", true) -> entity.drop(2).toIntOrNull(16)
                entity.startsWith('#') -> entity.drop(1).toIntOrNull()
                else -> null
            }
        if (number != null) {
            when {
                number == 0 || number in 0xD800..0xDFFF || number > 0x10FFFF -> "\uFFFD"
                number <= 0xFFFF -> number.toChar().toString()
                else -> {
                    val code = number - 0x10000
                    "${(0xD800 + (code shr 10)).toChar()}${(0xDC00 + (code and 1023)).toChar()}"
                }
            }
        } else
            when (entity) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos",
                "rsquo",
                "lsquo" -> "'"
                "nbsp" -> " "
                "ndash" -> "–"
                "mdash" -> "—"
                "hellip" -> "…"
                "ldquo",
                "rdquo" -> "\""
                else -> match.value
            }
    }

fun plainText(html: String): String {
    val source = html.take(8 * 1024 * 1024)
    val output = StringBuilder()
    val blocks =
        setOf("p", "div", "li", "ul", "ol", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6")
    var anchorStart = -1
    var anchorHref: String? = null
    var index = 0
    while (index < source.length) {
        val open = source.indexOf('<', index)
        if (open < 0) {
            output.append(source.substring(index))
            break
        }
        output.append(source.substring(index, open))
        val close = source.indexOf('>', open + 1)
        if (close < 0) {
            output.append(source.substring(open))
            break
        }
        val tag = source.substring(open + 1, close).trim()
        val closing = tag.startsWith('/')
        val name = tag.removePrefix("/").takeWhile { it.isLetterOrDigit() }.lowercase()
        index = close + 1
        if (name == "script" || name == "style") {
            if (!closing) {
                val end = source.indexOf("</$name", index, ignoreCase = true)
                if (end < 0) break
                val endClose = source.indexOf('>', end)
                if (endClose < 0) break
                index = endClose + 1
            }
            continue
        }
        if (name == "br" || name in blocks) output.append('\n')
        if (name == "a" && !closing) {
            anchorStart = output.length
            anchorHref =
                Regex("(?i)\\bhref\\s*=\\s*([\"'])(.*?)\\1")
                    .find(tag)
                    ?.groupValues
                    ?.get(2)
                    ?.let(::decodeEntities)
        } else if (name == "a" && closing && anchorStart >= 0) {
            val label = decodeEntities(output.substring(anchorStart))
            val href = anchorHref
            if (
                label.startsWith('@') &&
                    '@' !in label.drop(1) &&
                    href != null &&
                    safeWebUrl(href) != null
            ) {
                output.deleteRange(anchorStart, output.length)
                output.append(label).append('@').append(Url(href).host)
            }
            anchorStart = -1
            anchorHref = null
        }
    }
    return decodeEntities(output.toString())
        .replace("\r\n", "\n")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
