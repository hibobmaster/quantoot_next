package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.*
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException

internal fun inlineQuoteUrl(html: String): String? {
    val attribute = Regex("(?:^|\\s)(class|href)\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    for (anchor in Regex("<a\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(html)) {
        val attributes = attribute.findAll(anchor.groupValues[1]).associate {
            it.groupValues[1].lowercase() to it.groupValues[2]
        }
        if (attributes["class"]?.split(Regex("\\s+"))?.contains("quote-inline") == true)
            return safeWebUrl(attributes["href"].orEmpty().replace("&amp;", "&"))
    }
    return null
}

internal fun mayResolveQuote(quote: Quote?): Boolean = quote == null || quote.state == "accepted"

@Composable
internal fun LinkPreview(ui: UiContext, card: PreviewCard) {
    val url = safeWebUrl(card.url) ?: return
    if (card.title.isBlank()) return
    val image = safeWebUrl(card.image.orEmpty())
    val domain = Url(url).host
    OutlinedCard(onClick = { ui.open(url) }, modifier = Modifier.fillMaxWidth()) {
        if (image != null) AsyncImage(
            image, card.imageDescription,
            Modifier.fillMaxWidth().height(if (card.type == "photo") 220.dp else 156.dp),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.providerName?.takeIf { it.isNotBlank() } ?: domain,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (card.description.isNotBlank()) Text(card.description, maxLines = 3,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun QuotedPost(ui: UiContext, status: Status) {
    val quote = status.quote
    val id = quote?.quotedStatusId
    val url = remember(status.content) { inlineQuoteUrl(status.content) }
    if (quote == null && url == null) return
    if (!mayResolveQuote(quote)) {
        QuoteUnavailable()
        return
    }
    var loaded by remember(status.id, id, url) { mutableStateOf<Status?>(null) }
    var loading by remember(status.id, id, url) { mutableStateOf(false) }
    val embedded = quote?.quotedStatus
    LaunchedEffect(ui.api, embedded, id, url) {
        if (embedded != null) return@LaunchedEffect
        loading = true
        try {
            loaded = ui.statuses.read(
                load = {
                    if (!id.isNullOrBlank()) ui.api.status(id)
                    else url?.let { ui.api.search(it, type = "statuses").statuses.firstOrNull() }
                },
                values = { listOfNotNull(it) },
            )
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { loaded = null }
        finally { loading = false }
    }
    val target = (embedded ?: loaded)?.let { ui.statuses.project(it) }
    if (target != null) {
        StatusCard(ui, target, nested = true)
    } else if (loading) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(tr("mastodon_quoted_post"), style = MaterialTheme.typography.bodySmall)
            }
        }
    } else QuoteUnavailable()
}

@Composable
private fun QuoteUnavailable() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Text(tr("mastodon_unavailable_quote"), Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
