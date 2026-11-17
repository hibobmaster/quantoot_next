package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.Status
import kotlin.test.*

class NotificationPresentationTest {
    @Test
    fun actorWordingCountsEveryoneAfterTheFirstWhileAvatarsStayCapped() {
        for (count in listOf(1, 8, 9, 14)) {
            val actors = NotificationActors(List(count) { Account(id = "actor-$it") })
            assertEquals((0 until minOf(count, 8)).map { "actor-$it" }, actors.visible.map { it.id })
            assertEquals(count - 1, actors.othersCount)
            assertEquals(maxOf(0, count - 8), actors.overflowCount)
            val summary = Strings("en", "en").text("mastodon_notification_others",
                "count" to actors.othersCount.toString(), "action" to "favorited:")
            assertEquals("and ${count - 1} others favorited:", summary)
        }
    }

    @Test
    fun interactionPreviewCannotRevealTheBodyBehindAContentWarning() {
        val status = Status(content = "<p>Private plot twist</p>", spoilerText = "Spoilers")
        assertEquals("Spoilers", interactionPreviewText(status))
        assertEquals("Private plot twist", interactionPreviewText(status.copy(spoilerText = "")))
    }

    @Test
    fun interactionPreviewUsesSafeTextInsteadOfActiveHtml() {
        assertEquals("Hello & welcome", interactionPreviewText(Status(
            content = "<script>secret()</script><p>Hello &amp; <a href='https://example.org'>welcome</a></p>")))
    }
}
