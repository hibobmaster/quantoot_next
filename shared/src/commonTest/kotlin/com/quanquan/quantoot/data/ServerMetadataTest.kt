package com.quanquan.quantoot.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ServerMetadataTest {
    private fun TestScope.api(body: String): MastodonApi {
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = UnconfinedTestDispatcher(testScheduler)
            addHandler { request ->
                assertEquals("/api/v2/instance", request.url.encodedPath)
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        return MastodonApi("social.example", client = HttpClient(engine))
    }

    @Test
    fun serverInformationRetainsRulesContactArtworkAndUsage() = runTest {
        val api = api("""{
            "title":"Our community","domain":"social.example","version":"4.5.0",
            "thumbnail":{"url":"https://cdn.example/banner.png"},
            "usage":{"users":{"active_month":123}},
            "contact":{"email":"admin@social.example","account":{"id":"admin","username":"admin"}},
            "rules":[{"id":"1","text":"Be kind","hint":"Respect other people"}],
            "configuration":{
                "statuses":{"max_characters":700},
                "timelines_access":{"live_feeds":{"local":"public","remote":"authenticated"}}
            }
        }""")
        try {
            val server = api.serverInfo()
            assertEquals(700, server.maxCharacters)
            assertEquals("https://cdn.example/banner.png", server.thumbnail)
            assertEquals(123, server.activeMonthlyUsers)
            assertEquals("admin@social.example", server.contactEmail)
            assertEquals("admin", server.contactAccount?.id)
            assertEquals("Be kind", server.rules.single().text)
            assertEquals("Respect other people", server.rules.single().hint)
            assertEquals(true, server.guestLocalFeedAvailable)
            assertEquals(false, server.guestFederatedFeedAvailable)
        } finally { api.close() }
    }

    @Test
    fun absentOptionalMetadataDoesNotPreventServerSelection() = runTest {
        val api = api("""{"domain":"social.example","contact":null,"rules":null}""")
        try {
            val server = api.serverInfo()
            assertEquals(500, server.maxCharacters)
            assertTrue(server.rules.isEmpty())
            assertNull(server.thumbnail)
            assertNull(server.contactAccount)
            assertNull(server.guestLocalFeedAvailable)
            assertNull(server.guestFederatedFeedAvailable)
        } finally { api.close() }
    }
}
