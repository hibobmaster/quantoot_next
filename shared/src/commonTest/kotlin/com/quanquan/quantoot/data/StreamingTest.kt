package com.quanquan.quantoot.data

import com.quanquan.quantoot.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class StreamingTest {
    private fun envelope(event: String, stream: List<String> = listOf("user"), payload: String = "{\"id\":\"post\"}") = buildJsonObject {
        put("event", event)
        put("payload", payload)
        put("stream", JsonArray(stream.map(::JsonPrimitive)))
    }

    @Test
    fun createdEditedDeletedAndNotificationRetainSource() {
        val source = StreamSource.User
        assertIs<StreamEvent.Created>(parseStreamEvent(source, envelope("update")))
        assertIs<StreamEvent.Edited>(parseStreamEvent(source, envelope("status.update")))
        assertEquals(StreamEvent.Deleted(source, "post"), parseStreamEvent(source, envelope("delete", payload = "post")))
        assertIs<StreamEvent.Notify>(parseStreamEvent(source, envelope("notification")))
        assertNull(parseStreamEvent(source, envelope("filters_changed", payload = "")))
    }

    @Test
    fun subscriptionsKeepTagSeparateAndRejectMismatchedEnvelopes() {
        val tag = StreamSource.Hashtag("kotlin")
        assertEquals("hashtag" to "kotlin", streamSubscription(tag))
        assertEquals(tag, parseStreamEvent(tag, envelope("update", listOf("hashtag", "kotlin")))?.source)
        assertNull(parseStreamEvent(tag, envelope("update", listOf("hashtag", "swift"))))
        assertNull(parseStreamEvent(StreamSource.User, envelope("update", listOf("public"))))
        assertEquals(StreamSource.User, Timeline.Home.streamSource())
        assertEquals(StreamSource.Local, Timeline.Public(true).streamSource())
        assertEquals(StreamSource.Federated, Timeline.Public(false).streamSource())
        assertEquals(tag, Timeline.Hashtag("Kotlin").streamSource())
        assertNull(Timeline.AccountPosts("person").streamSource())
        assertFailsWith<IllegalStateException> { streamSubscription(StreamSource.LocalWrite) }
    }

    @Test
    fun guestDoesNotAttemptAnAnonymousWebSocket() = runTest {
        var requests = 0
        val api = MastodonApi("social.example", client = HttpClient(MockEngine {
            requests++
            error("A guest must use REST reconciliation")
        }))
        try {
            assertTrue(api.events(StreamSource.Local).toList().isEmpty())
            assertEquals(0, requests)
        } finally {
            api.close()
        }
    }
}
