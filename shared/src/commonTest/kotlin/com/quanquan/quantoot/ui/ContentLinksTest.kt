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
    fun federatedHashtagsUseTheTagFromTheReadingInstance() {
        val href = "https://remote.example/tags/Codeberg"
        val metadata = listOf(Tag("codeberg", "https://home.example/tags/codeberg"))
        val attributes = anchorAttributes(""" data-href="https://ordinary.example" class='mention hashtag' href="$href" """)
        assertEquals(href, attributes["href"])
        assertEquals("mention hashtag", attributes["class"])
        assertEquals(
            ContentTarget.Hashtag("codeberg"),
            contentTarget(href, mentions, metadata, "#Codeberg", markedHashtag = true),
        )
        assertEquals(ContentTarget.Hashtag("codeberg"), contentTarget(href, mentions, metadata, "#Codeberg"))
        val unicode = "https://remote.example/tags/%E5%A4%A9%E6%B0%A3"
        val unicodeMetadata = listOf(Tag("天氣", "https://home.example/tags/%E5%A4%A9%E6%B0%A3"))
        assertEquals(ContentTarget.Hashtag("天氣"), contentTarget(unicode, mentions, unicodeMetadata, "＃天氣"))
        assertEquals(ContentTarget.Hashtag("天氣"), contentTarget(unicode, mentions, emptyList(), "#天氣", markedHashtag = true))
    }

    @Test
    fun ordinaryLinksWithSimilarLabelsStayExternal() {
        val metadata = listOf(Tag("codeberg", "https://home.example/tags/codeberg"))
        for (url in listOf("https://codeberg.org/about", "https://remote.example/tags/other", "https://remote.example/tags/codeberg?mode=html")) {
            assertEquals(ContentTarget.External(url), contentTarget(url, mentions, metadata, "#Codeberg"))
        }
        val ordinary = "https://example.org"
        assertEquals(ContentTarget.External(ordinary), contentTarget(ordinary, mentions, metadata, "#Codeberg article", markedHashtag = true))
        assertNull(contentTarget("javascript:alert(1)", mentions, metadata, "#Codeberg", markedHashtag = true))
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
