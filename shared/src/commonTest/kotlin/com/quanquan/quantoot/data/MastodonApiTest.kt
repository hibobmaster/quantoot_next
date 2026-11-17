package com.quanquan.quantoot.data

import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.PickedMedia
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
class MastodonApiTest {
    private fun TestScope.engine(handler: MockRequestHandler): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = UnconfinedTestDispatcher(testScheduler)
                addHandler(handler)
            }
        )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun instanceAndExternalUrlsRejectAmbiguousAuthorities() {
        assertEquals("social.example", normalizeInstance(" Social.Example:443/ "))
        assertEquals("social.example:8443", normalizeInstance("https://SOCIAL.example:8443"))
        assertEquals("[2001:db8::1]:8443", normalizeInstance("https://[2001:db8::1]:8443"))
        listOf(
                "http://example.com",
                "https://user:pass@example.com",
                "example.com/path",
                "example.com?token=x",
                "example.com#fragment",
                "example.com\\@evil.test",
                "example.com:0",
                "a..test",
                "https://%65xample.com",
                "https://[:::]",
                "https://[1:2:3]",
            )
            .forEach { assertFailsWith<IllegalArgumentException>(it) { normalizeInstance(it) } }
        assertEquals("https://example.com/a?q=x#ok", safeWebUrl("https://example.com/a?q=x#ok"))
        listOf(
                "javascript:alert(1)",
                "data:text/html,x",
                "https://user@example.com",
                "https://example.com\n",
                "https://example.com\\evil",
            )
            .forEach { assertNull(safeWebUrl(it), it) }
    }

    @Test
    fun textKeepsParagraphsAndRemoteMentionsWithoutExecutingMarkup() {
        assertEquals(
            "First & second\n\nHello @alice@remote.example\nworld <safe>",
            plainText(
                "<p>First &amp; second</p><p>Hello <a class=\"mention\" href=\"https://remote.example/@alice\">@<span>alice</span></a><br>world &lt;safe&gt;</p><script>bad()</script>"
            ),
        )
        assertEquals(
            "@alice@remote.example",
            plainText("<a href='https://remote.example/users/alice'>@alice@remote.example</a>"),
        )
        assertEquals("😀", plainText("&#x1f600;"))
    }

    @Test
    fun guestTimelineEncodesPagingAndNeverSendsAuthorization() = runTest {
        val http =
            HttpClient(
                engine { request ->
                    assertEquals("/api/v1/timelines/tag/tea%2Fcoffee", request.url.encodedPath)
                    assertEquals("old + &", request.url.parameters["max_id"])
                    assertEquals("new", request.url.parameters["since_id"])
                    assertEquals("40", request.url.parameters["limit"])
                    assertNull(request.headers[HttpHeaders.Authorization])
                    respond(
                        "[{\"id\":\"1\",\"account\":null,\"content\":null}]",
                        headers = jsonHeaders,
                    )
                }
            )
        val api = MastodonApi("social.example", client = http)
        try {
            val result = api.timeline(Timeline.Hashtag("tea/coffee"), "old + &", "new", 999)
            assertEquals("1", result.items.single().id)
            assertEquals(Account(), result.items.single().account)
            assertEquals("", result.items.single().content)
        } finally {
            api.close()
        }
    }

    @Test
    fun opaquePagingUsesEndpointSpecificLinkCursors() = runTest {
        val paths =
            listOf(
                "/api/v1/favourites",
                "/api/v1/bookmarks",
                "/api/v1/accounts/person/followers",
                "/api/v1/accounts/person/following",
            )
        val calls = mutableMapOf<String, Int>()
        val api =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine { request ->
                        val path = request.url.encodedPath
                        val count = calls.getOrElse(path) { 0 }
                        calls[path] = count + 1
                        val cursor = "opaque-${paths.indexOf(path)} + &"
                        assertEquals("social.example", request.url.host)
                        assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
                        assertEquals("20", request.url.parameters["limit"])
                        if (count == 0) {
                            assertNull(request.url.parameters["max_id"])
                            respond(
                                "[{\"id\":\"same-public-id\"}]",
                                headers =
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/json")
                                        append(
                                            HttpHeaders.Link,
                                            "<https://social.example$path?limit=20&max_id=${cursor.encodeURLParameter()}>; rel=\"next\", <https://social.example$path?limit=20&min_id=older>; rel=\"prev\"",
                                        )
                                    },
                            )
                        } else {
                            assertEquals(cursor, request.url.parameters["max_id"])
                            assertEquals(setOf("limit", "max_id"), request.url.parameters.names())
                            respond("[]", headers = jsonHeaders)
                        }
                    }
                ),
            )
        try {
            val favourites = api.timeline(Timeline.Favourites)
            val bookmarks = api.timeline(Timeline.Bookmarks)
            val followers = api.people("person", false)
            val following = api.people("person", true)
            api.timeline(Timeline.Favourites, favourites.nextCursor)
            api.timeline(Timeline.Bookmarks, bookmarks.nextCursor)
            api.people("person", false, followers.nextCursor)
            api.people("person", true, following.nextCursor)
            assertEquals(paths.associateWith { 2 }, calls)
        } finally {
            api.close()
        }
    }

    @Test
    fun explicitOpaqueCursorSurvivesRefreshAndNewApiInstance() = runTest {
        val requested = mutableListOf<String?>()
        fun api() =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine { request ->
                        val cursor = request.url.parameters["max_id"]
                        requested += cursor
                        val number =
                            when (cursor) {
                                null -> 1
                                "opaque-1" -> 2
                                "opaque-2" -> 3
                                "opaque-3" -> 4
                                else -> error("Unexpected cursor $cursor")
                            }
                        respond(
                            """[{"id":"public-$number"}]""",
                            headers =
                                Headers.build {
                                    append(HttpHeaders.ContentType, "application/json")
                                    append(
                                        HttpHeaders.Link,
                                        "<https://social.example/api/v1/bookmarks?limit=20&max_id=opaque-$number>; rel=\"next\"",
                                    )
                                },
                        )
                    }
                ),
            )
        val first = api()
        var tail: String? = null
        try {
            repeat(3) { tail = first.timeline(Timeline.Bookmarks, tail).nextCursor }
            first.timeline(Timeline.Bookmarks)
            assertEquals("public-4", first.timeline(Timeline.Bookmarks, tail).items.single().id)
        } finally {
            first.close()
        }
        val restored = api()
        try {
            assertEquals("public-4", restored.timeline(Timeline.Bookmarks, tail).items.single().id)
            assertEquals(
                listOf(null, "opaque-1", "opaque-2", null, "opaque-3", "opaque-3"),
                requested,
            )
        } finally {
            restored.close()
        }
    }

    @Test
    fun unsafeOrMalformedNextLinksDoNotExposeContinuation() = runTest {
        val path = "/api/v1/favourites"
        val invalid =
            listOf(
                    "https://foreign.example$path?limit=20&max_id=opaque",
                    "http://social.example$path?limit=20&max_id=opaque",
                    "https://social.example:8443$path?limit=20&max_id=opaque",
                    "https://user@social.example$path?limit=20&max_id=opaque",
                    "https://social.example/api/v1/bookmarks?limit=20&max_id=opaque",
                    "https://social.example$path?limit=40&max_id=opaque",
                    "https://social.example$path?limit=20&max_id=a&max_id=b",
                    "https://social.example$path?limit=20&max_id=opaque#fragment",
                    "https://social.example$path?limit=20&max_id=opaque&extra=true",
                    "https://social.example$path?limit=20",
                    "https://social.example$path?limit=20&max_id=%ZZ",
                    "https://social.example$path?limit=20&max_id=%0A",
                    "not-a-url",
                )
                .map { "<$it>; rel=\"next\"" } + "broken; rel=\"next\""
        for (link in invalid) {
            var calls = 0
            val api =
                MastodonApi(
                    "social.example",
                    "secret",
                    HttpClient(
                        engine {
                            calls++
                            respond(
                                "[{\"id\":\"public-id\"}]",
                                headers =
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/json")
                                        append(HttpHeaders.Link, link)
                                    },
                            )
                        }
                    ),
                )
            try {
                val page = api.timeline(Timeline.Favourites)
                assertNull(page.nextCursor, link)
                assertEquals(1, calls, link)
            } finally {
                api.close()
            }
        }
    }

    @Test
    fun privateQueriesFailBeforeNetworkButPublicReadsRemainAvailable() = runTest {
        var calls = 0
        val api =
            MastodonApi(
                "social.example",
                client =
                    HttpClient(
                        engine {
                            calls++
                            respond("[]", headers = jsonHeaders)
                        }
                    ),
            )
        try {
            assertEquals(
                401,
                assertFailsWith<ApiException> { api.timeline(Timeline.Home) }.statusCode,
            )
            assertEquals(401, assertFailsWith<ApiException> { api.notifications() }.statusCode)
            assertEquals(
                401,
                assertFailsWith<ApiException> { api.publish(StatusDraft("hello")) }.statusCode,
            )
            assertEquals(0, calls)
            assertEquals(emptyList(), api.timeline(Timeline.Public()).items)
            assertEquals(1, calls)
        } finally {
            api.close()
        }
    }

    @Test
    fun publishingSendsFormArraysAndStableOperationKey() = runTest {
        val api =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine { request ->
                        assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
                        assertEquals("operation-1", request.headers["Idempotency-Key"])
                        assertEquals(HttpMethod.Post, request.method)
                        val form =
                            parseQueryString(
                                (request.body as OutgoingContent.ByteArrayContent)
                                    .bytes()
                                    .decodeToString()
                            )
                        assertEquals(listOf("one", "two"), form.getAll("media_ids[]"))
                        assertEquals(
                            listOf("Tea & cake", "Coffee + pie"),
                            form.getAll("poll[options][]"),
                        )
                        assertEquals("", form["spoiler_text"])
                        assertEquals("false", form["sensitive"])
                        assertEquals("followers", form["quote_approval_policy"])
                        respond("{\"id\":\"new\"}", headers = jsonHeaders)
                    }
                ),
            )
        try {
            val draft =
                StatusDraft(
                    "Hi @alice@remote.example",
                    mediaIds = listOf("one", "two"),
                    poll = PollDraft(listOf("Tea & cake", "Coffee + pie")),
                    quotePolicy = "followers",
                )
            assertEquals("new", api.publish(draft, idempotencyKey = "operation-1").id)
        } finally {
            api.close()
        }
    }

    @Test
    fun editedPostCanClearContentWarningAndAttachments() = runTest {
        val api =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine { request ->
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("/api/v1/statuses/edit", request.url.encodedPath)
                        val form =
                            parseQueryString(
                                (request.body as OutgoingContent.ByteArrayContent)
                                    .bytes()
                                    .decodeToString()
                            )
                        assertEquals("", form["spoiler_text"])
                        assertEquals(listOf(""), form.getAll("media_ids[]"))
                        respond("{}", headers = jsonHeaders)
                    }
                ),
            )
        try {
            api.publish(StatusDraft("edited"), "edit")
        } finally {
            api.close()
        }
    }

    @Test
    fun filterUpdatesPreserveKeywordIdsAndDestroyRemovedKeywords() = runTest {
        var calls = 0
        val api =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine { request ->
                        calls++
                        if (request.method == HttpMethod.Get) {
                            respond(
                                "{\"id\":\"f\",\"keywords\":[{\"id\":\"keep\",\"keyword\":\"one\"},{\"id\":\"remove\",\"keyword\":\"two\"}]}",
                                headers = jsonHeaders,
                            )
                        } else {
                            val body =
                                boundedJson(
                                        (request.body as OutgoingContent.ByteArrayContent)
                                            .bytes()
                                            .decodeToString()
                                    )
                                    .jsonObject
                            val words = body.getValue("keywords_attributes").jsonArray
                            assertEquals("keep", words[0].jsonObject["id"]?.jsonPrimitive?.content)
                            assertEquals(
                                true,
                                words[0].jsonObject["whole_word"]?.jsonPrimitive?.boolean,
                            )
                            assertNull(words[1].jsonObject["id"])
                            assertEquals(
                                "remove",
                                words[2].jsonObject["id"]?.jsonPrimitive?.content,
                            )
                            assertEquals(
                                true,
                                words[2].jsonObject["_destroy"]?.jsonPrimitive?.boolean,
                            )
                            respond("{\"id\":\"f\"}", headers = jsonHeaders)
                        }
                    }
                ),
            )
        try {
            api.saveFilter(
                ContentFilter(
                    "f",
                    "Words",
                    keywords =
                        listOf(
                            FilterKeyword("keep", "edited", true),
                            FilterKeyword(keyword = "new"),
                        ),
                )
            )
            assertEquals(2, calls)
        } finally {
            api.close()
        }
    }

    @Test
    fun acceptedMediaWaitsUntilProcessingCompletes() = runTest {
        var calls = 0
        val api =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine { request ->
                        calls++
                        when (calls) {
                            1 -> {
                                assertEquals("/api/v2/media", request.url.encodedPath)
                                respond(
                                    "{\"id\":\"m\",\"url\":null}",
                                    HttpStatusCode.Accepted,
                                    jsonHeaders,
                                )
                            }
                            2 -> {
                                assertEquals("/api/v1/media/m", request.url.encodedPath)
                                respond("", HttpStatusCode.PartialContent, jsonHeaders)
                            }
                            else ->
                                respond(
                                    "{\"id\":\"m\",\"url\":\"https://cdn.example/m.png\"}",
                                    headers = jsonHeaders,
                                )
                        }
                    }
                ),
            )
        try {
            assertEquals(
                "https://cdn.example/m.png",
                api.upload(PickedMedia("photo.png", "image/png", byteArrayOf(1))).url,
            )
            assertEquals(3, calls)
        } finally {
            api.close()
        }
    }

    @Test
    fun uploadLimitsRejectBeforeNetwork() = runTest {
        var calls = 0
        val api =
            MastodonApi(
                "social.example",
                "secret",
                HttpClient(
                    engine {
                        calls++
                        respond("{}", headers = jsonHeaders)
                    }
                ),
            )
        try {
            assertFailsWith<IllegalArgumentException> {
                api.upload(PickedMedia("large", "image/png", ByteArray(30 * 1024 * 1024 + 1)))
            }
            assertEquals(0, calls)
        } finally {
            api.close()
        }
    }

    @Test
    fun responseLimitsRejectOversizedDeepAndExcessiveTokenJson() = runTest {
        val bodies =
            listOf(
                " ".repeat(MAX_RESPONSE_BYTES + 1),
                "[".repeat(65) + "]".repeat(65),
                "[" + List(100_001) { "0" }.joinToString(",") + "]",
            )
        for (body in bodies) {
            val api =
                MastodonApi(
                    "social.example",
                    client = HttpClient(engine { respond(body, headers = jsonHeaders) }),
                )
            try {
                assertFailsWith<ApiException> { api.trends() }
            } finally {
                api.close()
            }
        }
    }

    @Test
    fun nestedStatusesStopAtSixLevels() = runTest {
        var json = "{\"id\":\"8\",\"account\":{},\"content\":\"last\"}"
        for (id in 7 downTo 1) json =
            "{\"id\":\"$id\",\"account\":{},\"content\":\"post\",\"reblog\":$json}"
        val api =
            MastodonApi(
                "social.example",
                client = HttpClient(engine { respond(json, headers = jsonHeaders) }),
            )
        try {
            var status = api.status("1")
            repeat(5) { status = assertNotNull(status.reblog) }
            assertEquals("6", status.id)
            assertNull(status.reblog)
        } finally {
            api.close()
        }
    }

    @Test
    fun redirectsDoNotForwardTokensAndErrorsDoNotReflectSecrets() = runTest {
        var calls = 0
        val api =
            MastodonApi(
                "social.example",
                "private-token",
                HttpClient(
                    engine { request ->
                        calls++
                        assertEquals("social.example", request.url.host)
                        respond(
                            "private-token",
                            HttpStatusCode.Found,
                            headersOf(HttpHeaders.Location, "https://evil.example/steal"),
                        )
                    }
                ),
            )
        try {
            val error = assertFailsWith<ApiException> { api.verifyCredentials() }
            assertEquals(302, error.statusCode)
            assertFalse(error.message.contains("private-token"))
            assertEquals(1, calls)
        } finally {
            api.close()
        }
    }

    @Test
    fun requestDeadlineIncludesTheResponseBody() = runTest {
        val body = ByteChannel()
        val writer = backgroundScope.launch {
            body.writeStringUtf8("{\"id\":")
            awaitCancellation()
        }
        val api =
            MastodonApi(
                "social.example",
                client = HttpClient(engine { respond(body, headers = jsonHeaders) }),
            )
        try {
            val error = assertFailsWith<ApiException> { api.status("1") }
            assertEquals("The request timed out", error.message)
            assertEquals(20_000, testScheduler.currentTime)
        } finally {
            api.close()
            writer.cancel()
        }
    }

    @Test
    fun closingClientStopsStreamReconnectBackoffImmediately() = runTest {
        var attempts = 0
        val api =
            MastodonApi(
                "social.example",
                accessToken = "test-token",
                client =
                    HttpClient(
                        engine {
                            attempts++
                            respond("unavailable", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                        }
                    ),
            )
        val collector = backgroundScope.launch { api.events(StreamSource.User).collect() }
        runCurrent()
        assertEquals(1, attempts)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2, attempts)
        api.close()
        runCurrent()
        assertTrue(collector.isCancelled)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, attempts)
    }

    @Test
    fun callerCancellationIsPreserved() = runTest {
        val started = CompletableDeferred<Unit>()
        val api =
            MastodonApi(
                "social.example",
                client =
                    HttpClient(
                        engine {
                            started.complete(Unit)
                            awaitCancellation()
                        }
                    ),
            )
        try {
            val job = launch { api.status("1") }
            started.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        } finally {
            api.close()
        }
    }
}
