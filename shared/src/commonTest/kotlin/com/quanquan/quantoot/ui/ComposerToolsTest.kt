package com.quanquan.quantoot.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.quanquan.quantoot.model.CustomEmoji
import kotlin.test.*

class ComposerToolsTest {
    @Test
    fun suggestionsFollowTheCaretAndReplaceTheWholeTag() {
        val input = TextFieldValue("Hello #kotlin world", TextRange(9))
        val token = assertNotNull(hashtagAtCaret(input))
        assertEquals("ko", token.query)
        val result = completeHashtag(input, token, "KotlinMultiplatform")
        assertEquals("Hello #KotlinMultiplatform world", result.text)
        assertEquals('w', result.text[result.selection.end])
    }

    @Test
    fun unicodeTagsAndAnEmptyHashCanRequestSuggestions() {
        assertEquals("春天", hashtagAtCaret(TextFieldValue("(#春天", TextRange(4)))?.query)
        assertEquals("", hashtagAtCaret(TextFieldValue("#", TextRange(1)))?.query)
    }

    @Test
    fun linksSelectionsAndInvalidTokensDoNotRequestTags() {
        assertNull(hashtagAtCaret(TextFieldValue("https://example.org/#tag", TextRange(24))))
        assertNull(hashtagAtCaret(TextFieldValue("#a#b", TextRange(4))))
        assertNull(hashtagAtCaret(TextFieldValue("#tag", TextRange(0, 4))))
        assertNull(hashtagAtCaret(TextFieldValue("#tag ", TextRange(5))))
    }

    @Test
    fun emojiInsertionReplacesSelectionAndRetainsSurroundingText() {
        val result = insertAtSelection(TextFieldValue("Hello old world", TextRange(6, 9)), ":wave:")
        assertEquals("Hello :wave: world", result.text)
        assertEquals(TextRange(12), result.selection)
    }

    @Test
    fun pickerHonorsVisibilityAndGroupsWithoutLosingInlineEmojiMetadata() {
        val all = listOf(
            CustomEmoji("hidden", visibleInPicker = false, category = "Animals"),
            CustomEmoji("cat", category = "Animals"),
            CustomEmoji("wave"),
            CustomEmoji("cat", category = "Duplicate"),
        )
        val groups = visibleEmojiGroups(all)
        assertEquals(listOf("Animals", ""), groups.keys.toList())
        assertEquals(listOf("cat", "wave"), groups.values.flatten().map { it.shortcode })
        assertEquals(4, all.size)
    }
}
