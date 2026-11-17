package com.quanquan.quantoot.ui

import androidx.compose.ui.text.TextRange
import com.quanquan.quantoot.model.*
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ComposerDraftTest {
    private val settings = AccountSettings(
        privacy = "private", language = "zh", sensitive = true, quotePolicy = "followers",
    )

    @Test
    fun defaultsCheckEachChoiceWhenTheRequestCompletes() = runTest {
        val draft = ComposerDraft(Screen.Compose(), null)
        val response = CompletableDeferred<AccountSettings>()
        val load = launch { draft.applyDefaults(response.await()) }
        runCurrent()
        draft.text = "Typed while preferences load"
        draft.chooseVisibility("direct")
        response.complete(settings)
        load.join()
        assertEquals("direct", draft.visibility)
        assertEquals("zh", draft.language)
        assertTrue(draft.sensitive)
        assertEquals("followers", draft.quotePolicy)
        assertEquals("Typed while preferences load", draft.text)
    }

    @Test
    fun revertingAChoiceStillProtectsItFromLateDefaults() {
        val draft = ComposerDraft(Screen.Compose(), null)
        draft.chooseVisibility("direct")
        draft.chooseVisibility("public")
        draft.chooseLanguage("en")
        draft.chooseLanguage("")
        draft.chooseQuotePolicy("nobody")
        draft.chooseQuotePolicy("public")
        draft.chooseSensitive(true)
        draft.chooseSensitive(false)
        draft.applyDefaults(settings)
        assertEquals("public", draft.visibility)
        assertEquals("", draft.language)
        assertEquals("public", draft.quotePolicy)
        assertFalse(draft.sensitive)
        assertFalse(draft.dirty)
    }

    @Test
    fun applyingDefaultsRebasesOnlyUntouchedFields() {
        val draft = ComposerDraft(Screen.Compose(), null)
        draft.chooseLanguage("en")
        draft.applyDefaults(settings)
        assertEquals("en", draft.language)
        assertEquals("private", draft.visibility)
        assertTrue(draft.dirty)
        draft.chooseLanguage("")
        assertFalse(draft.dirty)
        draft.applyDefaults(settings.copy(privacy = "unlisted", language = "fr"))
        assertEquals("unlisted", draft.visibility)
        assertEquals("", draft.language)
        assertFalse(draft.dirty)
    }

    @Test
    fun settingsChoicesAreDirtyAndRevertingThemIsClean() {
        val choices: List<Pair<(ComposerDraft) -> Unit, (ComposerDraft) -> Unit>> = listOf(
            { draft: ComposerDraft -> draft.chooseVisibility("direct") } to { it.chooseVisibility("public") },
            { draft: ComposerDraft -> draft.chooseLanguage("en") } to { it.chooseLanguage("") },
            { draft: ComposerDraft -> draft.chooseQuotePolicy("nobody") } to { it.chooseQuotePolicy("public") },
            { draft: ComposerDraft -> draft.chooseSensitive(true) } to { it.chooseSensitive(false) },
        )
        choices.forEach { (choose, revert) ->
            val draft = ComposerDraft(Screen.Compose(), null)
            choose(draft)
            assertTrue(draft.dirty)
            revert(draft)
            assertFalse(draft.dirty)
        }
    }

    @Test
    fun defaultsAloneAndCursorMovesStayClean() {
        val draft = ComposerDraft(Screen.Compose(), null)
        draft.applyDefaults(settings)
        assertFalse(draft.dirty)
        draft.textInput = draft.textInput.copy(selection = TextRange.Zero)
        assertFalse(draft.dirty)
    }

    @Test
    fun replyVisibilityAndEditFieldsKeepRoutePrecedence() {
        val reply = ComposerDraft(Screen.Compose(reply = Status(visibility = "direct")), null)
        reply.applyDefaults(settings)
        assertEquals("direct", reply.visibility)
        assertEquals("zh", reply.language)
        assertFalse(reply.dirty)

        val edit = ComposerDraft(Screen.Compose(edit = Status(
            content = "<p>Original</p>", spoilerText = "Warning", visibility = "unlisted",
            language = "de", sensitive = false,
        )), null)
        edit.applyDefaults(settings)
        assertEquals("unlisted", edit.visibility)
        assertEquals("de", edit.language)
        assertFalse(edit.sensitive)
        assertEquals("", edit.quotePolicy)
        assertNull(edit.payload().quotePolicy)
        assertFalse(edit.dirty)
    }

    @Test
    fun unchangedEditsWithMediaOrPollsAreCleanAndAttachmentAltChangesAreDirty() {
        val media = MediaAttachment(id = "image", description = "Original alt")
        val draft = ComposerDraft(Screen.Compose(edit = Status(
            content = "<p>Original</p>", spoilerText = "Warning", mediaAttachments = listOf(media),
        )), null)
        assertFalse(draft.dirty)
        draft.attachments = listOf(media.copy(url = "https://media.example/refreshed.png"))
        assertFalse(draft.dirty)
        draft.attachments = listOf(media.copy(description = "New alt"))
        assertTrue(draft.dirty)
        draft.attachments = listOf(media)
        assertFalse(draft.dirty)
        draft.attachments = emptyList()
        assertTrue(draft.dirty)

        val poll = ComposerDraft(Screen.Compose(edit = Status(
            content = "<p>Original</p>", poll = Poll(options = listOf(PollOption("A"), PollOption("B"))),
        )), null)
        assertFalse(poll.dirty)
        poll.duration = 3600
        assertTrue(poll.dirty)
        poll.duration = 86400
        assertFalse(poll.dirty)
    }

    @Test
    fun publishingAndDirtyTrackingUseTheSameEffectivePayload() {
        val draft = ComposerDraft(Screen.Compose(reply = Status(id = "parent", visibility = "direct")), null)
        draft.applyDefaults(settings)
        draft.text = "Answer"
        draft.spoiler = "Warning"
        draft.attachments = listOf(MediaAttachment(id = "media"))
        assertEquals(StatusDraft(
            text = "Answer", visibility = "direct", language = "zh", spoilerText = "Warning",
            sensitive = true, inReplyToId = "parent", mediaIds = listOf("media"),
            quotePolicy = "followers",
        ), draft.payload())
        assertTrue(draft.dirty)
    }

    @Test
    fun operationKeysRemainStableForRetriesAndCursorChanges() {
        val draft = ComposerDraft(Screen.Compose(), null)
        draft.text = "First"
        val first = draft.keyFor(draft.payload())
        assertEquals(first, draft.keyFor(draft.payload()))
        draft.textInput = draft.textInput.copy(selection = TextRange.Zero)
        assertEquals(first, draft.keyFor(draft.payload()))
        draft.chooseVisibility("direct")
        val changed = draft.keyFor(draft.payload())
        assertNotEquals(first, changed)
        assertEquals(changed, draft.keyFor(draft.payload()))
    }

    @Test
    fun unknownEditPolicyIsPreservedUntilTheUserChoosesAndNullLanguageStaysUnset() {
        val draft = ComposerDraft(Screen.Compose(edit = Status(content = "<p>Original</p>")),
            Account(source = AccountSource(language = "en")))
        assertNull(draft.payload().language)
        assertNull(draft.payload().quotePolicy)
        draft.text = "Edited text"
        assertNull(draft.payload().quotePolicy)
        draft.chooseQuotePolicy("followers")
        assertEquals("followers", draft.payload().quotePolicy)
        draft.chooseQuotePolicy("")
        assertNull(draft.payload().quotePolicy)
        draft.text = "Original"
        assertFalse(draft.dirty)
    }

    @Test
    fun existingPollEditsCannotProduceAnUnsafeUpdatePayload() {
        val draft = ComposerDraft(Screen.Compose(edit = Status(
            poll = Poll(options = listOf(PollOption("A"), PollOption("B"))),
        )), null)
        assertFalse(draft.dirty)
        draft.text = "Only editing the text"
        assertFailsWith<IllegalStateException> { draft.payload() }
        draft.pollEnabled = false
        assertFailsWith<IllegalStateException> { draft.payload() }
    }

    @Test
    fun newPollConfigurationIsPartOfTheEffectivePayload() {
        val draft = ComposerDraft(Screen.Compose(), null)
        draft.pollEnabled = true
        draft.options = listOf(" A ", "B")
        draft.duration = 3600
        draft.multiple = true
        draft.hideTotals = true
        assertEquals(PollDraft(listOf("A", "B"), 3600, true, true), draft.payload().poll)
        assertTrue(draft.dirty)
        draft.pollEnabled = false
        assertNull(draft.payload().poll)
        assertFalse(draft.dirty)
    }
}
