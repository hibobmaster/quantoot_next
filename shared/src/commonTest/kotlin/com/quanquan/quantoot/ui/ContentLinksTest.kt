package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.Mention
import com.quanquan.quantoot.model.Tag
import kotlin.test.*

class ContentLinksTest {
    private val mentions = listOf(Mention(id = "local-42", url = "https://remote.example/@alice"))
    private val tags = listOf(Tag("Tea", "https://remote.example/tags/Tea"))

    @Test
    fun matchedMetadataSuppliesTheLocalIdAndTagName() {
        assertEquals(
            ContentTarget.Profile("local-42"),
            contentTarget("HTTPS://REMOTE.EXAMPLE:443/@alice", mentions, tags),
        )
        assertEquals(
            ContentTarget.Hashtag("Tea"),
            contentTarget("https://remote.example/tags/Tea", mentions, tags),
        )
    }

    @Test
    fun unknownRemotePathsNeverBecomeLocalIdsOrHashtags() {
        listOf(
            "https://other.example/@alice",
            "https://remote.example/users/local-42",
            "https://remote.example/tags/tea",
            "https://remote.example/@alice?redirect=other",
            "https://remote.example/@alice#other",
            "http://remote.example/@alice",
        ).forEach { url ->
            assertEquals(ContentTarget.External(url), contentTarget(url, mentions, tags), url)
        }
    }

    @Test
    fun unsafeLinksRemainPlainTextEvenWhenMetadataMatches() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,test",
            "https://user:password@remote.example/@alice",
            "https://remote.example\\@evil.example/@alice",
            "https://remote.example/@alice\n",
            "//remote.example/@alice",
        ).forEach { url ->
            assertNull(contentTarget(url, listOf(Mention(id = "42", url = url)), listOf(Tag("x", url))), url)
        }
    }

    @Test
    fun blankIdentifiersAndUnsafeMetadataDoNotCreateInternalTargets() {
        val url = "https://remote.example/@alice"
        assertEquals(
            ContentTarget.External(url),
            contentTarget(url, listOf(Mention(url = url)), listOf(Tag(url = url))),
        )
        assertEquals(
            ContentTarget.External(url),
            contentTarget(url, listOf(Mention(id = "42", url = "$url ")), emptyList()),
        )
    }
}
