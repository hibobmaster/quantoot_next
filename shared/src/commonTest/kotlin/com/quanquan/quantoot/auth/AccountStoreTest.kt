package com.quanquan.quantoot.auth

import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.platform.KeyValueStore
import com.quanquan.quantoot.platform.PickedMedia
import com.quanquan.quantoot.platform.PlatformServices
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*

class AccountStoreTest {
    @Test
    fun firstRunHasNoImplicitInstanceAndPersistsLanguageAndGuest() = runTest {
        val fixture = Fixture(this)
        fixture.store.restore()
        assertNull(fixture.store.state.value.instance)
        assertNull(fixture.store.client())
        fixture.store.setLocale("zh-TW")
        fixture.store.browse("https://EXAMPLE.social/")
        val restored = fixture.newStore()
        restored.restore()
        assertEquals("example.social", restored.state.value.instance)
        assertEquals("zh-TW", restored.state.value.locale)
        assertNull(restored.state.value.activeSession)
    }

    @Test
    fun authorizationIsDurableBeforeBrowserAndPkceUsesAdvertisedVersion() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        val url = Url(fixture.platform.authorization!!)
        assertEquals("read write follow", url.parameters["scope"])
        assertEquals("S256", url.parameters["code_challenge_method"])
        assertEquals(43, url.parameters["code_challenge"]?.length)
        val app = fixture.forms.single { it.first == "/api/v1/apps" }.second
        assertEquals("read write follow", app["scopes"])
        assertEquals("quantoot://mastodon-auth", app["redirect_uris"])
        assertNotNull(fixture.platform.secureStorage.values["mastodon_auth_state_v3"])
        val restored = fixture.newStore()
        restored.acceptCallback(fixture.callback())
        assertEquals("1", restored.state.value.activeSession?.account?.id)
        val token = fixture.forms.single { it.first == "/oauth/token" }.second
        assertEquals("read write follow", token["scope"])
        assertEquals(43, token["code_verifier"]?.length)
    }

    @Test
    fun unsupportedPkceOmitsChallengeAndVerifier() = runTest {
        val fixture = Fixture(this, apiVersion = 2)
        fixture.store.login("example.social")
        assertNull(Url(fixture.platform.authorization!!).parameters["code_challenge"])
        fixture.store.acceptCallback(fixture.callback())
        assertNull(fixture.forms.single { it.first == "/oauth/token" }.second["code_verifier"])
    }

    @Test
    fun mismatchedAndInvalidCallbacksLeaveTheValidAttemptUsable() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        val valid = fixture.callback()
        val invalid =
            listOf(
                "quantoot://mastodon-auth?state=wrong&code=one",
                valid.replace("mastodon-auth?", "mastodon-auth/other?"),
                valid + "&state=duplicate",
                valid + "#fragment",
                valid.replace("quantoot://", "https://"),
                valid.replace("code=1", "error=access_denied"),
            )
        for (callback in invalid) {
            fixture.store.acceptCallback(callback)
            assertNotNull(fixture.store.state.value.error)
            assertEquals(0, fixture.tokenRequests)
        }
        fixture.store.acceptCallback(valid)
        assertEquals("1", fixture.store.state.value.activeSession?.account?.id)
        assertEquals(1, fixture.tokenRequests)
    }

    @Test
    fun callbackIsConsumedOnceAcrossConcurrentCallsAndRestart() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        val callback = fixture.callback()
        val first = async { fixture.store.acceptCallback(callback) }
        val second = async { fixture.store.acceptCallback(callback) }
        first.await()
        second.await()
        val restored = fixture.newStore()
        restored.acceptCallback(callback)
        assertEquals(1, fixture.tokenRequests)
        assertEquals(1, restored.state.value.sessions.size)
    }

    @Test
    fun expiryAndClockRollbackNeverExchangeCode() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        fixture.now += 600_000
        fixture.store.acceptCallback(fixture.callback())
        assertEquals(0, fixture.tokenRequests)
        assertNotNull(fixture.store.state.value.error)
        fixture.now = 999
        fixture.store.acceptCallback(fixture.callback())
        assertEquals(0, fixture.tokenRequests)
    }

    @Test
    fun multipleAccountsSwitchRemoveAndPreserveGuestInstance() = runTest {
        val fixture = Fixture(this)
        fixture.store.browse("guest.social")
        fixture.store.login("one.social")
        fixture.store.acceptCallback(fixture.callback("one"))
        val first = fixture.store.state.value.activeKey!!
        val captured = fixture.store.client()!!
        fixture.store.login("two.social")
        fixture.store.acceptCallback(fixture.callback("two"))
        val second = fixture.store.state.value.activeKey!!
        assertEquals(2, fixture.store.state.value.sessions.size)
        assertEquals("one.social", captured.instance)
        captured.close()
        fixture.store.switchAccount(first)
        assertEquals("one.social", fixture.store.state.value.instance)
        fixture.store.updateAccount(Account(id = "one", username = "updated"))
        assertEquals("updated", fixture.store.state.value.activeSession?.account?.username)
        fixture.store.removeAccount(first)
        assertEquals(second, fixture.store.state.value.activeKey)
        fixture.store.removeAccount(second)
        assertEquals("guest.social", fixture.store.state.value.instance)
        assertTrue(fixture.store.state.value.sessions.isEmpty())
        val restored = fixture.newStore()
        restored.restore()
        assertEquals("guest.social", restored.state.value.instance)
    }

    @Test
    fun sameAccountReauthenticationChangesAuthorizationRevisionAndToken() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        fixture.store.acceptCallback(fixture.callback())
        val original = fixture.store.state.value
        val captured = fixture.store.client()!!
        fixture.accessToken = "rotated-token"
        fixture.store.login("example.social")
        assertEquals(
            original.authorizationRevision,
            fixture.store.state.value.authorizationRevision,
        )
        fixture.store.acceptCallback(fixture.callback())
        assertEquals(original.activeKey, fixture.store.state.value.activeKey)
        assertEquals(
            original.authorizationRevision + 1,
            fixture.store.state.value.authorizationRevision,
        )
        val refreshed = fixture.store.client()!!
        try {
            assertEquals("1", refreshed.verifyCredentials().id)
            fixture.accessToken = "secret-token"
            assertEquals("1", captured.verifyCredentials().id)
        } finally {
            captured.close()
            refreshed.close()
        }
        val revision = fixture.store.state.value.authorizationRevision
        fixture.store.updateAccount(Account(id = "1", username = "updated"))
        fixture.store.setLocale("zh")
        fixture.store.switchAccount("missing")
        assertNotNull(fixture.store.state.value.error)
        fixture.store.clearError()
        assertEquals(revision, fixture.store.state.value.authorizationRevision)
        fixture.accessToken = "rotated-token"
        fixture.store.login("example.social")
        fixture.store.acceptCallback(fixture.callback())
        assertEquals(revision, fixture.store.state.value.authorizationRevision)
        fixture.store.restore()
        assertEquals(revision, fixture.store.state.value.authorizationRevision)
    }

    @Test
    fun danglingActiveSelectionRestoresTheFirstValidatedSavedAccount() = runTest {
        val fixture = Fixture(this)
        val raw =
            "{\"sessions\":[{\"instance_host\":\"example.social\",\"access_token\":\"secret-token\",\"account\":{\"id\":\"1\"}}],\"active_session_key\":\"missing\"}"
        fixture.platform.secureStorage.values["mastodon_auth_state_v3"] = raw
        fixture.store.restore()
        assertNull(fixture.store.state.value.error)
        assertEquals("example.social::1", fixture.store.state.value.activeKey)
        assertEquals(raw, fixture.platform.secureStorage.values["mastodon_auth_state_v3"])
    }

    @Test
    fun aFailedSecureWritePublishesNoNewAccountAndLeaksNoSecrets() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        fixture.failFinalSave = true
        fixture.store.acceptCallback(fixture.callback())
        assertTrue(fixture.store.state.value.sessions.isEmpty())
        assertNotNull(fixture.store.state.value.error)
        assertFalse(fixture.store.state.value.toString().contains("secret-token"))
        assertFalse(fixture.store.state.value.toString().contains("storage-secret"))
    }

    @Test
    fun callbackStorageFailureLeavesAttemptAvailableForRetry() = runTest {
        val fixture = Fixture(this)
        fixture.store.login("example.social")
        fixture.platform.secureStorage.failWrite = true
        fixture.store.acceptCallback(fixture.callback())
        assertEquals(0, fixture.tokenRequests)
        fixture.platform.secureStorage.failWrite = false
        fixture.store.acceptCallback(fixture.callback())
        assertEquals(1, fixture.tokenRequests)
        assertNotNull(fixture.store.state.value.activeSession)
    }

    @Test
    fun failedPendingSaveNeverOpensTheBrowser() = runTest {
        val fixture = Fixture(this)
        fixture.store.restore()
        fixture.platform.secureStorage.failWrite = true
        fixture.store.login("example.social")
        assertNull(fixture.platform.authorization)
        assertNotNull(fixture.store.state.value.error)
    }

    @Test
    fun oversizedOrDeepTokenResponsesNeverCreateAnAccount() = runTest {
        for (body in
            listOf(
                "{\"access_token\":\"" + "a".repeat(2 * 1024 * 1024) + "\"}",
                "[".repeat(65) + "0" + "]".repeat(65),
            )) {
            val fixture = Fixture(this)
            fixture.store.login("example.social")
            fixture.tokenBodyOverride = body
            fixture.store.acceptCallback(fixture.callback())
            assertTrue(fixture.store.state.value.sessions.isEmpty())
            assertNotNull(fixture.store.state.value.error)
        }
    }

    @Test
    fun corruptSecureSnapshotIsPreservedAndCannotBeOverwritten() = runTest {
        val fixture = Fixture(this)
        val corrupt =
            "{\"sessions\":[{\"instance_host\":\"example.social\",\"access_token\":\"secret-token\",\"account\":{\"id\":\"\"}}]}"
        fixture.platform.secureStorage.values["mastodon_auth_state_v3"] = corrupt
        fixture.store.restore()
        assertNotNull(fixture.store.state.value.error)
        fixture.store.browse("other.social")
        fixture.store.login("other.social")
        assertEquals(corrupt, fixture.platform.secureStorage.values["mastodon_auth_state_v3"])
        assertNull(fixture.platform.authorization)
        fixture.platform.secureStorage.values["mastodon_auth_state_v3"] = "{\"sessions\":[]}"
        fixture.store.restore()
        fixture.store.browse("other.social")
        assertEquals("other.social", fixture.store.state.value.instance)
    }

    @Test
    fun duplicateAccountsAndMalformedSecureDataSurfaceRecoverableErrors() = runTest {
        val session =
            "{\"instance_host\":\"example.social\",\"access_token\":\"secret-token\",\"account\":{\"id\":\"1\"}}"
        for (raw in
            listOf(
                "{}",
                "not-json",
                "{\"sessions\":[$session,$session]}",
                "{\"sessions\":[],\"active_session_key\":\"missing\"}",
            )) {
            val fixture = Fixture(this)
            fixture.platform.secureStorage.values["mastodon_auth_state_v3"] = raw
            fixture.store.restore()
            assertTrue(fixture.store.state.value.restored)
            assertNotNull(fixture.store.state.value.error)
            assertEquals(raw, fixture.platform.secureStorage.values["mastodon_auth_state_v3"])
        }
    }

    @Test
    fun storageReadFailureCanBeRetriedWithoutDeletingSavedAccounts() = runTest {
        val fixture = Fixture(this)
        fixture.platform.secureStorage.failRead = true
        fixture.store.restore()
        assertNotNull(fixture.store.state.value.error)
        fixture.platform.secureStorage.failRead = false
        fixture.store.restore()
        assertNull(fixture.store.state.value.error)
        fixture.store.browse("example.social")
        assertEquals("example.social", fixture.store.state.value.instance)
    }

    @Test
    fun legacyPreferenceSessionsAreMovedToSecureSnapshot() = runTest {
        val fixture = Fixture(this)
        fixture.platform.preferences.values["mastodon_session_v1"] =
            "{\"instance_host\":\"example.social\",\"access_token\":\"secret-token\",\"account\":{\"id\":\"1\"}}"
        fixture.store.restore()
        assertEquals("1", fixture.store.state.value.activeSession?.account?.id)
        assertNull(fixture.platform.preferences.values["mastodon_session_v1"])
        assertNotNull(fixture.platform.secureStorage.values["mastodon_auth_state_v3"])
        assertFalse(fixture.store.state.value.toString().contains("secret-token"))
    }

    @Test
    fun cancellationDoesNotBecomeAUserError() = runTest {
        val platform = FakePlatform()
        val entered = CompletableDeferred<Unit>()
        val store =
            AccountStore(
                platform,
                {
                    HttpClient(
                        mockEngine {
                            entered.complete(Unit)
                            CompletableDeferred<Unit>().await()
                            error("unreachable")
                        }
                    )
                },
            )
        val work = async { store.login("example.social") }
        entered.await()
        work.cancel()
        work.join()
        assertTrue(work.isCancelled)
        assertFalse(store.state.value.busy)
        assertNull(store.state.value.error)
    }
}

private class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    var failRead = false
    var failWrite = false

    override suspend fun read(key: String): String? {
        if (failRead) error("storage-secret")
        return values[key]
    }

    override suspend fun write(key: String, value: String?) {
        if (failWrite) error("storage-secret")
        if (value == null) values.remove(key) else values[key] = value
    }
}

private class FakePlatform : PlatformServices {
    override val preferences = MemoryStore()
    override val secureStorage = MemoryStore()
    override val oauthRedirects = MutableSharedFlow<String>()
    override val foreground = MutableStateFlow(true)
    override val languageTag = "en"
    var authorization: String? = null

    override fun openAuthorization(url: String) {
        check(secureStorage.values["mastodon_auth_state_v3"] != null)
        authorization = url
    }

    override fun openUrl(url: String) = Unit

    override fun share(text: String) = Unit

    override suspend fun pickMedia(limit: Int): List<PickedMedia> = emptyList()

    override suspend fun saveMedia(url: String, name: String) = Unit

    override suspend fun saveImageBytes(bytes: ByteArray, name: String) = Unit
}

private class Fixture(private val scope: TestScope, private val apiVersion: Int = 3) {
    val platform = FakePlatform()
    var now = 1_000L
    var tokenRequests = 0
    var failFinalSave = false
    var tokenBodyOverride: String? = null
    var accessToken = "secret-token"
    val forms = mutableListOf<Pair<String, Parameters>>()
    private var accountId = "1"
    val store = newStore()

    fun newStore() =
        AccountStore(
            platform,
            {
                HttpClient(
                    scope.mockEngine { request ->
                        val path = request.url.encodedPath
                        val form = (request.body as? TextContent)?.text?.let(::parseQueryString)
                        if (form != null) forms.add(path to form)
                        val body =
                            when (path) {
                                "/api/v2/instance" ->
                                    "{\"api_versions\":{\"mastodon\":$apiVersion}}"
                                "/api/v1/apps" ->
                                    "{\"client_id\":\"client\",\"client_secret\":\"client-secret\"}"
                                "/oauth/token" -> {
                                    tokenRequests++
                                    accountId = form?.get("code") ?: "1"
                                    tokenBodyOverride ?: "{\"access_token\":\"$accessToken\"}"
                                }
                                "/api/v1/accounts/verify_credentials" -> {
                                    assertEquals(
                                        "Bearer $accessToken",
                                        request.headers[HttpHeaders.Authorization],
                                    )
                                    if (failFinalSave) platform.secureStorage.failWrite = true
                                    "{\"id\":\"$accountId\",\"username\":\"test\"}"
                                }
                                else -> error("Unexpected request")
                            }
                        respond(
                            body,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                )
            },
            { now },
        )

    fun callback(code: String = "1"): String {
        val oauthState = Url(platform.authorization!!).parameters["state"]!!
        return "quantoot://mastodon-auth?state=$oauthState&code=$code"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.mockEngine(handler: MockRequestHandler): MockEngine =
    MockEngine(
        MockEngineConfig().apply {
            dispatcher = UnconfinedTestDispatcher(testScheduler)
            addHandler(handler)
        }
    )
