package com.quanquan.quantoot.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*

@Composable
fun HtmlContent(
    ui: UiContext,
    html: String,
    modifier: Modifier = Modifier,
    emojis: List<CustomEmoji> = emptyList(),
    mentions: List<Mention> = emptyList(),
    tags: List<Tag> = emptyList(),
) {
    val color = MaterialTheme.colorScheme.primary
    val shortcodes = emojis.map { it.shortcode }.toSet()
    val inline = emojiContent(emojis)
    val annotated =
        remember(html, color, emojis, mentions, tags, ui) {
            val content = buildAnnotatedString {
                val source =
                    Regex("<(script|style)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
                        .replace(html, "")
                fun fragment(value: String) =
                    plainText("\uE000$value\uE001").removePrefix("\uE000").removeSuffix("\uE001")
                val links =
                    Regex(
                        "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>",
                        RegexOption.IGNORE_CASE,
                    )
                var cursor = 0
                for (match in links.findAll(source)) {
                    appendEmojiText(
                        fragment(source.substring(cursor, match.range.first)),
                        shortcodes,
                    )
                    val href = safeWebUrl(match.groupValues[1].replace("&amp;", "&"))
                    val label = plainText(match.groupValues[2])
                    if (href == null) appendEmojiText(label, shortcodes)
                    else
                        withLink(
                            LinkAnnotation.Url(
                                href,
                                TextLinkStyles(SpanStyle(color = color)),
                                LinkInteractionListener {
                                    when (val target = contentTarget(href, mentions, tags)) {
                                        is ContentTarget.Profile -> ui.navigate(Screen.Profile(target.id))
                                        is ContentTarget.Hashtag -> ui.navigate(Screen.Feed(Timeline.Hashtag(target.tag), "#${target.tag}"))
                                        is ContentTarget.External -> ui.open(target.url)
                                        null -> Unit
                                    }
                                },
                            )
                        ) {
                            appendEmojiText(label, shortcodes)
                        }
                    cursor = match.range.last + 1
                }
                appendEmojiText(fragment(source.substring(cursor)), shortcodes)
            }
            val start = content.text.indexOfFirst { !it.isWhitespace() }
            val end = content.text.indexOfLast { !it.isWhitespace() }
            if (start < 0) AnnotatedString("") else content.subSequence(start, end + 1)
        }
    SelectionContainer {
        Text(
            annotated,
            modifier,
            inlineContent = inline,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

