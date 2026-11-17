package com.quanquan.quantoot.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.quanquan.quantoot.model.CustomEmoji

internal data class HashtagToken(val start: Int, val end: Int, val query: String)

internal fun hashtagAtCaret(value: TextFieldValue): HashtagToken? {
    if (!value.selection.collapsed) return null
    val cursor = value.selection.end
    if (cursor !in 0..value.text.length) return null
    fun boundary(char: Char) = char.isWhitespace() || char in "([{"
    var start = cursor
    while (start > 0 && !boundary(value.text[start - 1])) start--
    val prefix = value.text.substring(start, cursor)
    if (!prefix.startsWith('#') || prefix.drop(1).any { !it.isLetterOrDigit() && it != '_' })
        return null
    var end = cursor
    while (end < value.text.length && (value.text[end].isLetterOrDigit() || value.text[end] == '_')) end++
    return HashtagToken(start, end, prefix.drop(1))
}

internal fun completeHashtag(value: TextFieldValue, token: HashtagToken, name: String): TextFieldValue {
    val prefix = value.text.take(token.start) + "#" + name
    val suffix = value.text.drop(token.end)
    val text = prefix + (if (suffix.startsWith(' ')) "" else " ") + suffix
    return TextFieldValue(text, TextRange(prefix.length + 1))
}

internal fun insertAtSelection(value: TextFieldValue, insertion: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    return TextFieldValue(
        value.text.replaceRange(start, end, insertion),
        TextRange(start + insertion.length),
    )
}

internal fun visibleEmojiGroups(emojis: List<CustomEmoji>): Map<String, List<CustomEmoji>> =
    emojis.filter { it.visibleInPicker }.distinctBy { it.shortcode }
        .groupBy { it.category.orEmpty().trim() }

internal val postLanguages = listOf(
    "ar" to "العربية", "ca" to "Català", "cs" to "Čeština", "da" to "Dansk",
    "de" to "Deutsch", "el" to "Ελληνικά", "en" to "English", "es" to "Español",
    "fi" to "Suomi", "fr" to "Français", "he" to "עברית", "hi" to "हिन्दी",
    "hu" to "Magyar", "id" to "Bahasa Indonesia", "it" to "Italiano", "ja" to "日本語",
    "ko" to "한국어", "nl" to "Nederlands", "no" to "Norsk", "pl" to "Polski",
    "pt" to "Português", "ro" to "Română", "ru" to "Русский", "sv" to "Svenska",
    "th" to "ไทย", "tr" to "Türkçe", "uk" to "Українська", "vi" to "Tiếng Việt",
    "zh" to "简体中文", "zh-TW" to "繁體中文",
)
