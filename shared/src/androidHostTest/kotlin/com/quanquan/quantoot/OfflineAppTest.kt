package com.quanquan.quantoot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.auth.AccountStore
import com.quanquan.quantoot.auth.FakePlatform
import com.quanquan.quantoot.auth.Session
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.Poll
import com.quanquan.quantoot.model.PollOption
import com.quanquan.quantoot.model.Status
import com.quanquan.quantoot.ui.AdaptiveWindow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h700dp-mdpi")
class OfflineAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun draftSurvivesFoldingAndCanBeDiscarded() {
        openApp(OfflineMastodon(), initialWidth = 900)
        newPost()
        val text = "A draft written before folding"
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.runOnIdle { width.intValue = 390 }
        compose.onNode(hasSetTextAction()).assertTextContains(text)
        compose.runOnIdle { width.intValue = 900 }
        compose.onNode(hasSetTextAction()).assertTextContains(text)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard draft?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains(text)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard").performClick()
        newPost()
        compose.onNodeWithText("Publish").assertIsNotEnabled()
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun privatePostAppearsOnTheSignedInProfile() {
        val server = OfflineMastodon()
        openApp(server)
        newPost()
        val text = "A private post from offline QA"
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithContentDescription("Visibility, Public").performClick()
        compose.onNodeWithText("Private mention").performClick()
        compose.onNodeWithText("Publish").performClick()
        waitFor(hasText("Profile") and hasClickAction())
        selectTab("Profile")
        waitFor(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
        assertEquals("direct", server.posts.value.single().visibility)
    }

    @Test
    fun profileEditIsSavedAndReadBackWhenReopened() {
        val server = OfflineMastodon()
        openApp(server)
        selectTab("Profile")
        compose.onNode(waitFor(hasText("Edit profile") and hasClickAction())).performClick()
        val updatedName = "Updated offline name"
        compose.onNode(hasSetTextAction() and hasText(server.account.value.displayName))
            .performTextReplacement(updatedName)
        compose.onNodeWithText("Save").performClick()
        val editProfile = waitFor(hasText("Edit profile") and hasClickAction())
        assertEquals(updatedName, server.account.value.displayName)
        compose.onNode(editProfile).performClick()
        compose.onNode(hasSetTextAction() and hasText(updatedName)).assertIsDisplayed()
    }

    @Test
    fun guestCanChangeTabsWhileCapabilitiesAreLoading() {
        val metadata = CompletableDeferred<String>()
        val server = OfflineMastodon(metadata)
        openApp(server, signedIn = false)
        compose.onNode(hasText("Explore") and isSelected()).assertExists()
        selectTab("Notifications")
        metadata.complete(PUBLIC_TIMELINES)
        compose.waitUntil(5_000) {
            server.platform.preferences.values[CAPABILITIES_KEY] == "10"
        }
        compose.onNode(hasText("Notifications") and isSelected()).assertExists()
        selectTab("Home")
        compose.onNodeWithText("Local").assertIsDisplayed()
        compose.onNodeWithText("Federated").assertDoesNotExist()
    }

    @Test
    fun guestUsesCachedTimelineCapabilitiesWhileMetadataIsUnavailable() {
        val server = OfflineMastodon(CompletableDeferred())
        server.platform.preferences.values[CAPABILITIES_KEY] = "10"
        openApp(server, signedIn = false)
        selectTab("Home")
        compose.onNodeWithText("Local").assertIsDisplayed()
        compose.onNodeWithText("Federated").assertDoesNotExist()
    }

    @Test
    fun singleChoicePollCanBeClearedBeforeVotingAndShowsLocalDeadline() {
        val server = OfflineMastodon()
        val deadline = "2099-11-27T23:30:45.000Z"
        val poll = Poll(
            id = "9", expiresAt = deadline,
            options = listOf(PollOption("Open source"), PollOption("Microsoft 365")),
        )
        server.posts.value = listOf(
            Status(id = "42", account = server.account.value, content = "Which tool would you use?", poll = poll),
        )
        val previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        try {
            openApp(server)
            compose.onNode(waitFor(hasContentDescription("View replies, 0 replies"))).performClick()
            compose.onNode(waitFor(hasText("Conversation"))).assertIsDisplayed()
            compose.onNode(hasText("Ends Nov 28, 2099", substring = true) and hasText("7:30", substring = true))
                .assertIsDisplayed()
            compose.onNodeWithText(deadline, substring = true).assertDoesNotExist()

            val first = compose.onNode(hasText("Open source") and isToggleable())
            val second = compose.onNode(hasText("Microsoft 365") and isToggleable())
            val vote = compose.onNodeWithText("Vote")
            vote.assertIsNotEnabled()
            first.performTouchInput { click(center) }.assertIsOn()
            vote.assertIsEnabled()
            first.performTouchInput { click(center) }.assertIsOff()
            vote.assertIsNotEnabled()
            assertEquals(emptyList<Int>(), server.posts.value.single().poll?.ownVotes)

            first.performClick().assertIsOn()
            second.performClick().assertIsOn()
            first.assertIsOff()
            vote.performClick()
            compose.waitUntil(5_000) { server.posts.value.single().poll?.voted == true }
            assertEquals(listOf(1), server.posts.value.single().poll?.ownVotes)
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Vote").fetchSemanticsNodes().isEmpty() }
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    private val width = mutableIntStateOf(390)

    private fun openApp(server: OfflineMastodon, initialWidth: Int = 390, signedIn: Boolean = true) {
        width.intValue = initialWidth
        val store = server.store(signedIn)
        compose.setContent {
            Box(Modifier.width(width.intValue.dp).fillMaxHeight()) { QuanTootApp(store, AdaptiveWindow()) }
        }
        waitFor(hasText(if (signedIn) "Home" else "Explore") and isSelected())
    }

    private fun waitFor(matcher: SemanticsMatcher): SemanticsMatcher {
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return matcher
    }

    private fun newPost() = compose.onNodeWithContentDescription("New post").performClick()

    private fun selectTab(name: String) = compose.onNode(hasText(name) and hasClickAction() and isSelectable()).performClick()
}

private const val INSTANCE = "offline.example"
private const val CAPABILITIES_KEY = "guest_timeline_access_v1_offline.example"
private const val PUBLIC_TIMELINES =
    """{"configuration":{"timelines_access":{"live_feeds":{"local":"public","remote":"disabled"}}}}"""

private class OfflineMastodon(private val metadata: CompletableDeferred<String>? = null) {
    val platform = FakePlatform()
    val account = MutableStateFlow(Account(id = "1", username = "qa", acct = "qa", displayName = "Offline QA"))
    val posts = MutableStateFlow(emptyList<Status>())

    fun store(signedIn: Boolean): AccountStore {
        platform.preferences.values["app_locale"] = "en"
        platform.preferences.values["mastodon_instance_host_v1"] = INSTANCE
        if (signedIn) {
            val session = Session(INSTANCE, "offline-test-token", account.value)
            platform.secureStorage.values["mastodon_auth_state_v3"] = buildJsonObject {
                put("active_session_key", session.key)
                put("sessions", Json.encodeToJsonElement(listOf(session)))
            }.toString()
        }
        return AccountStore(platform, httpClientFactory = ::client)
    }

    private fun client() = HttpClient(MockEngine { request ->
        check(request.url.host == INSTANCE)
        val path = request.url.encodedPath
        val body = when (path) {
            "/api/v2/instance" -> metadata?.await() ?: PUBLIC_TIMELINES
            "/api/v1/accounts/1", "/api/v1/accounts/verify_credentials" -> Json.encodeToString(account.value)
            "/api/v1/timelines/home" -> Json.encodeToString(
                posts.value.takeUnless { request.url.parameters["max_id"] != null }.orEmpty()
            )
            "/api/v1/statuses/42" -> Json.encodeToString(posts.value.single())
            "/api/v1/statuses/42/context" -> "{}"
            "/api/v1/polls/9/votes" -> {
                check(request.method == HttpMethod.Post)
                val choices = parseQueryString(request.body.toByteArray().decodeToString())
                    .getAll("choices[]").orEmpty().map(String::toInt)
                val post = posts.value.single()
                val poll = checkNotNull(post.poll).copy(voted = true, ownVotes = choices)
                posts.value = listOf(post.copy(poll = poll))
                Json.encodeToString(poll)
            }
            "/api/v1/accounts/1/statuses" -> Json.encodeToString(
                posts.value.takeUnless { request.url.parameters["pinned"] == "true" ||
                    request.url.parameters["max_id"] != null }.orEmpty()
            )
            "/api/v1/statuses" -> {
                check(request.method == HttpMethod.Post)
                check(request.headers[HttpHeaders.Authorization] == "Bearer offline-test-token")
                val values = parseQueryString(request.body.toByteArray().decodeToString())
                val post = Status(
                    id = "42",
                    account = account.value,
                    content = values["status"]!!,
                    visibility = values["visibility"]!!,
                )
                posts.value = listOf(post)
                Json.encodeToString(post)
            }
            "/api/v1/accounts/update_credentials" -> {
                check(request.method == HttpMethod.Patch)
                check(request.headers[HttpHeaders.Authorization] == "Bearer offline-test-token")
                val multipart = request.body.toByteArray().decodeToString()
                val displayNameHeader = Regex("name=\"?display_name\"?").find(multipart)!!
                val value = multipart.substring(displayNameHeader.range.last + 1)
                    .substringAfter("\r\n\r\n").substringBefore("\r\n")
                check(value.isNotBlank())
                account.value = account.value.copy(displayName = value)
                Json.encodeToString(account.value)
            }
            "/api/v2/notifications/unread_count" -> """{"count":0}"""
            "/api/v1/preferences", "/api/v1/markers" -> "{}"
            "/api/v1/streaming" -> return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
            else -> "[]"
        }
        respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    })
}
