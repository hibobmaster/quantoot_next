package com.quanquan.quantoot.ui

import com.quanquan.quantoot.data.apiJson
import com.quanquan.quantoot.data.mastodonAccountDeletionUrl
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppStoreReadinessTest {
    @Test
    fun markedSensitivePostsHaveNoRevealActionOnIos() {
        val post = Status(sensitive = true, spoilerText = "Content warning")
        assertEquals(PostContentRestriction.Hidden, post.contentRestriction(true, "home"))
        assertEquals(PostContentRestriction.Warning("Content warning"), post.contentRestriction(false, "home"))
    }

    @Test
    fun serverFiltersUseTheirContextsAndHideTakesPrecedence() {
        val post = apiJson.decodeFromString<Status>("""
            {"filtered":[
              {"filter":{"title":"Spoilers","context":["home","thread"],"filter_action":"warn"}},
              {"filter":{"title":"Blocked phrase","context":["thread"],"filter_action":"hide"}}
            ]}
        """.trimIndent())
        assertEquals(PostContentRestriction.Warning("Spoilers"), post.contentRestriction(false, "home"))
        assertEquals(PostContentRestriction.Hidden, post.contentRestriction(false, "thread"))
        assertEquals(PostContentRestriction.None, post.contentRestriction(false, "public"))
    }

    @Test
    fun blockingImmediatelySuppressesCachedPostsAndBoosts() {
        val author = Account(id = "author")
        val post = Status(id = "post", account = author)
        val boost = Status(id = "boost", account = Account(id = "other"), reblog = post)
        val statuses = StatusProjection()
        statuses.setAccountBlocked(author.id, true)
        assertNull(statuses.project(post))
        assertNull(statuses.project(boost))
        statuses.setAccountBlocked(author.id, false)
        assertNotNull(statuses.project(post))
    }

    @Test
    fun accountDeletionUsesTheActiveServersHttpsOrigin() {
        assertEquals("https://mastodon.example/settings/delete", mastodonAccountDeletionUrl("https://Mastodon.Example:443/"))
        assertEquals("https://mastodon.example:8443/settings/delete", mastodonAccountDeletionUrl("mastodon.example:8443"))
        assertFailsWith<IllegalArgumentException> { mastodonAccountDeletionUrl("https://mastodon.example@other.example") }
        assertFailsWith<IllegalArgumentException> { mastodonAccountDeletionUrl("http://mastodon.example") }
    }
}
