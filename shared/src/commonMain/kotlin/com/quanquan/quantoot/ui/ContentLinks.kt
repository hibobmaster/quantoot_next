package com.quanquan.quantoot.ui

import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.Mention
import com.quanquan.quantoot.model.Tag
import io.ktor.http.Url

sealed interface ContentTarget {
    data class Profile(val id: String) : ContentTarget

    data class Hashtag(val tag: String) : ContentTarget

    data class External(val url: String) : ContentTarget
}

fun contentTarget(href: String, mentions: List<Mention>, tags: List<Tag>): ContentTarget? {
    val safe = safeWebUrl(href) ?: return null
    val target = normalizedContentUrl(safe)
    mentions.firstOrNull { it.id.isNotBlank() && normalizedContentUrl(it.url) == target }?.let {
        return ContentTarget.Profile(it.id)
    }
    tags.firstOrNull { it.name.isNotBlank() && normalizedContentUrl(it.url) == target }?.let {
        return ContentTarget.Hashtag(it.name)
    }
    return ContentTarget.External(safe)
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
