package com.quanquan.quantoot.ui

import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.Mention
import com.quanquan.quantoot.model.Tag
import io.ktor.http.Url
import io.ktor.http.decodeURLPart

sealed interface ContentTarget {
    data class Profile(val id: String) : ContentTarget

    data class Hashtag(val tag: String) : ContentTarget

    data class External(val url: String) : ContentTarget
}

private val hashtagName = Regex("[\\p{L}\\p{M}\\p{N}_·・\u200c]+")
private val anchorAttribute = Regex("""(?:^|\s+)([^\s"'<>/=]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>]+))""")

internal fun anchorAttributes(source: String): Map<String, String> =
    anchorAttribute.findAll(source).associate { match ->
        match.groupValues[1].lowercase() to (2..4).firstNotNullOf { match.groups[it]?.value }
    }

fun contentTarget(
    href: String,
    mentions: List<Mention>,
    tags: List<Tag>,
    label: String = "",
    markedHashtag: Boolean = false,
): ContentTarget? {
    val safe = safeWebUrl(href) ?: return null
    val target = normalizedContentUrl(safe)
    mentions.firstOrNull { it.id.isNotBlank() && normalizedContentUrl(it.url) == target }?.let {
        return ContentTarget.Profile(it.id)
    }
    tags.firstOrNull { it.name.isNotBlank() && normalizedContentUrl(it.url) == target }?.let {
        return ContentTarget.Hashtag(it.name)
    }
    val name = label.trim().removePrefix("#").removePrefix("＃")
    if (name != label.trim() && hashtagName.matches(name)) {
        val tag = tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
        // Federated posts can keep the author's link while the API supplies a tag URL on this instance.
        if (markedHashtag || (tag != null && standardTagPath(target, name))) {
            return ContentTarget.Hashtag(tag?.name ?: name)
        }
    }
    return ContentTarget.External(safe)
}

private fun standardTagPath(url: ContentUrl?, name: String): Boolean {
    if (url == null || url.query.isNotEmpty() || url.fragment.isNotEmpty() || !url.path.startsWith("/tags/")) return false
    val segment = url.path.removePrefix("/tags/").removeSuffix("/")
    return runCatching { segment.decodeURLPart().equals(name, ignoreCase = true) }.getOrDefault(false)
}

private data class ContentUrl(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val query: String,
    val fragment: String,
)

private fun normalizedContentUrl(value: String): ContentUrl? {
    val safe = safeWebUrl(value) ?: return null
    val url = Url(safe)
    return ContentUrl(
        url.protocol.name.lowercase(),
        url.host.lowercase(),
        url.port,
        url.encodedPath.ifEmpty { "/" },
        url.encodedQuery,
        url.encodedFragment,
    )
}
