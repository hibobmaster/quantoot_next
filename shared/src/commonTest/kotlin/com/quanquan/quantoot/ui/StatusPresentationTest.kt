package com.quanquan.quantoot.ui

import kotlin.test.*

class StatusPresentationTest {
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
