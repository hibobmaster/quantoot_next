package com.quanquan.quantoot.ui

import kotlin.test.*

class StatusPresentationTest {
    @Test
    fun countsFitInTheActionRowUsingTheSelectedLocalesUnits() {
        val english = Strings("en", "zh-TW")
        assertEquals("999", english.compactCount(999))
        assertEquals("1.2K", english.compactCount(1_299))
        assertEquals("1M", english.compactCount(1_000_000))
        assertEquals("2.1B", english.compactCount(Int.MAX_VALUE))
        assertEquals("9999", Strings("zh", "en").compactCount(9_999))
        assertEquals("1.9万", Strings("zh", "en").compactCount(19_999))
        assertEquals("1億", Strings("system", "zh-Hant-TW").compactCount(100_000_000))
    }

    @Test
    fun unknownVisibilityCannotBePresentedAsPublicInAnyLocale() {
        for (locale in listOf("en", "zh", "zh-TW")) {
            val strings = Strings(locale, locale)
            for (raw in listOf("", "future-visibility", "PUBLIC")) {
                val label = strings.text(PostVisibility.from(raw).labelKey)
                assertEquals(strings.text("mastodon_visibility_unavailable"), label)
                assertNotEquals(strings.text("mastodon_public"), label)
                assertNotEquals("mastodon_visibility_unavailable", label)
            }
        }
    }

    @Test
    fun directAndFollowersPostsRetainDifferentReadableVisibility() {
        for (locale in listOf("en", "zh", "zh-TW")) {
            val strings = Strings(locale, locale)
            val labels = listOf("public", "unlisted", "private", "direct")
                .map { strings.text(PostVisibility.from(it).labelKey) }
            assertEquals(4, labels.distinct().size)
            assertEquals(strings.text("mastodon_direct"), labels.last())
        }
    }
}
