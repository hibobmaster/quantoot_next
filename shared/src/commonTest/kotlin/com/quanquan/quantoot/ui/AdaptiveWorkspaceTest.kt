package com.quanquan.quantoot.ui

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.toMutableStateList
import kotlin.test.*

class AdaptiveWorkspaceTest {
    @Test
    fun narrowWindowShowsOnePaneAndWideWindowKeepsTheDetailBesideIt() {
        val narrow = workspaceLayout(360f, 780f, emptyList(), true, true, false, null)
        assertEquals(PaneBounds(0f, 0f, 360f, 780f), narrow.primary)
        assertNull(narrow.secondary)
        assertNull(narrow.rail)

        val wide = workspaceLayout(780f, 640f, emptyList(), true, true, false, null)
        assertEquals(80f, assertNotNull(wide.rail).width)
        val detail = assertNotNull(wide.secondary)
        assertTrue(wide.primary.right < detail.left)
        assertTrue(detail.width >= 319f)
    }

    @Test
    fun bookPanesAndVerticalControlsRespectReservedRegions() {
        val window = workspaceLayout(
            942f, 720f,
            listOf(WindowRegion(435f, 0f, 475f, 720f, true), WindowRegion(915f, 20f, 942f, 110f, false)),
            true, true, false, VerticalControls.Right,
        )
        assertEquals(435f, window.primary.right)
        val detail = assertNotNull(window.secondary)
        assertEquals(475f, detail.left)
        val rail = assertNotNull(window.rail)
        assertEquals(915f, rail.right)
        assertEquals(rail.left, detail.right)

        val narrowBook = listOf(WindowRegion(450f, 0f, 490f, 655f, true))
        val list = workspaceLayout(858f, 655f, narrowBook, false, true, false, VerticalControls.Right)
        val split = workspaceLayout(858f, 655f, narrowBook, true, true, false, VerticalControls.Right)
        assertNull(list.secondary)
        assertEquals(450f, list.primary.right)
        assertEquals(PaneBounds(794f, 0f, 858f, 655f), list.rail)
        assertEquals(list.rail, split.rail)
        assertEquals(304f, assertNotNull(split.secondary).width)
    }

    @Test
    fun tabletopKeepsVideoAndControlsOnOppositeSidesOfTheHinge() {
        val horizontalHinge = WindowRegion(0f, 185f, 690f, 205f, true)
        val options = workspaceLayout(690f, 416f, listOf(horizontalHinge), true, false, false, null,
            singleMaxWidth = Float.POSITIVE_INFINITY, splitUnfolded = false)
        assertEquals(185f, options.primary.bottom)
        assertEquals(205f, assertNotNull(options.secondary).top)
        val flat = workspaceLayout(690f, 416f, emptyList(), true, false, false, null, splitUnfolded = false)
        assertNull(flat.secondary)
    }

    @Test
    fun conversationAndDraftSurviveARecreatedWindow() {
        val draftRoute = Screen.Compose()
        val stack = listOf(Screen.Thread("17", focusReplyId = "19"), draftRoute).toMutableStateList()
        val saved = with(screenStackSaver) { SaverScope { true }.save(stack) }
        assertEquals(stack.toList(), screenStackSaver.restore(assertNotNull(saved))?.toList())

        val draft = ComposerDraft(draftRoute, null).apply {
            text = "A draft in progress"
            spoiler = "Releases"
            chooseVisibility("private")
            pollEnabled = true
            options = listOf("Yes", "Later")
        }
        val payload = draft.payload()
        val retry = draft.keyFor(payload)
        val restored = ComposerDraft(draftRoute, null)
        restored.restoreAfterWindowChange(assertNotNull(draft.saveForWindowChange()))
        assertEquals(payload, restored.payload())
        assertEquals(retry, restored.keyFor(payload))
        assertTrue(restored.dirty)
    }

    @Test
    fun nativeEditorUsesCurrentActionsAndBlocksHiddenTabs() {
        val changes = mutableListOf<NativeNavigationState?>()
        val invoked = mutableListOf<Any>()
        val bridge = NativeNavigation(changes::add)
        val shell = NativeNavigationState(
            listOf(NativeDestination("Explore", "Explore")), "Explore", 0, "Explore",
            null, showsTabs = true, editing = false,
            actions = listOf(NativeToolbarAction(NativeAction.Settings, "Settings"))
        )
        bridge.update(shell, invoked::add, invoked::add)
        bridge.select("Explore")
        bridge.perform(NativeAction.Settings)

        val editor = shell.copy(showsTabs = false, editing = true, actions = emptyList())
        bridge.update(editor, invoked::add, invoked::add)
        bridge.edit(listOf(NativeToolbarAction(NativeAction.Confirm, "Publish", false)), invoked::add)
        bridge.select("Explore")
        bridge.perform(NativeAction.Confirm)
        assertEquals(listOf<Any>("Explore", NativeAction.Settings), invoked)

        val enabled = NativeToolbarAction(NativeAction.Confirm, "Publish")
        bridge.edit(listOf(enabled), invoked::add)
        bridge.perform(NativeAction.Confirm)
        assertEquals(listOf(enabled), changes.last()?.actions)
        assertEquals(NativeAction.Confirm, invoked.last())
        bridge.clear()
        assertNull(changes.last())
    }
}
