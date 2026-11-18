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
    style: TextStyle = MaterialTheme.typography.bodyLarge,
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
                val links = Regex("""<a(?=\s)((?:[^'">]|"[^"]*"|'[^']*')*)>([\s\S]*?)</a\s*>""", RegexOption.IGNORE_CASE)
                var cursor = 0
                for (match in links.findAll(source)) {
                    appendEmojiText(
                        fragment(source.substring(cursor, match.range.first)),
                        shortcodes,
                    )
                    val attributes = anchorAttributes(match.groupValues[1])
                    val href = attributes["href"]?.replace("&amp;", "&")
                    val label = plainText(match.groupValues[2])
                    val markedHashtag = attributes["class"].hasLinkToken("hashtag") || attributes["rel"].hasLinkToken("tag")
                    val target = href?.let { contentTarget(it, mentions, tags, label, markedHashtag) }
                    if (href == null || target == null) appendEmojiText(label, shortcodes)
                    else
                        withLink(
                            LinkAnnotation.Url(
                                href,
                                TextLinkStyles(SpanStyle(color = color)),
                                LinkInteractionListener {
                                    when (target) {
                                        is ContentTarget.Profile -> ui.navigate(Screen.Profile(target.id))
                                        is ContentTarget.Hashtag -> ui.navigate(Screen.Feed(Timeline.Hashtag(target.tag), "#${target.tag}"))
                                        is ContentTarget.External -> ui.open(target.url)
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
    if (annotated.isEmpty()) return
    SelectionContainer {
        Text(
            annotated,
            modifier,
            inlineContent = inline,
            style = style,
        )
    }
}

private fun String?.hasLinkToken(token: String): Boolean =
    this?.split(Regex("\\s+"))?.any { it.equals(token, ignoreCase = true) } == true

