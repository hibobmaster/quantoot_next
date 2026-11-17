package com.quanquan.quantoot.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SettingsNavigationTest {
    @Test
    fun directChildNavigationAddsAnIndexAndBackReturnsToTheCaller() {
        val caller = Screen.Profile("author")
        val child = Screen.Settings(SettingsSection.Server)
        val routes = navigateTo(listOf(caller), child)

        assertEquals(listOf(caller, Screen.Settings(), child), routes)
        val afterChildBack = routes.dropLast(1)
        assertEquals(listOf(caller, Screen.Settings()), afterChildBack)
        assertEquals(listOf(caller), afterChildBack.dropLast(1))
        assertEquals(listOf(Screen.Settings(), child), navigateTo(emptyList(), child))
    }

    @Test
    fun openingAChildFromTheIndexKeepsOneIndexAndSiblingSelectionReplacesTheChild() {
        val caller = Screen.Profile("author")
        val index = listOf(caller, Screen.Settings())
        val privacy = Screen.Settings(SettingsSection.Privacy)
        val filters = Screen.Settings(SettingsSection.Filters)
        val routes = navigateTo(index, privacy)

        assertEquals(index + privacy, routes)
        val siblingRoutes = navigateTo(routes, filters)
        assertEquals(index + filters, siblingRoutes)
        assertEquals(index, siblingRoutes.dropLast(1))
    }

    @Test
    fun accountsRoundTripReturnsToTheSameSettingsChild() {
        val child = Screen.Settings(SettingsSection.PostingDefaults)
        val routes = navigateTo(listOf(Screen.Profile("author")), child)
        val accounts = navigateTo(routes, Screen.Accounts)

        assertEquals(routes + Screen.Accounts, accounts)
        assertEquals(routes, accounts.dropLast(1))
        assertEquals(child, accounts.dropLast(1).last())
    }

    @Test
    fun indexAndChildSuppressDuplicateTopNavigation() {
        val index = listOf(Screen.Profile("author"), Screen.Settings())
        assertSame(index, navigateTo(index, Screen.Settings()))

        val child = Screen.Settings(SettingsSection.PrivacyPolicy)
        val routes = navigateTo(index, child)
        assertSame(routes, navigateTo(routes, child.copy()))
    }

    @Test
    fun reopeningTheIndexRetainsOrdinaryPushBehavior() {
        val child = Screen.Settings(SettingsSection.Licenses)
        val routes = listOf(Screen.Settings(), child, Screen.Accounts)

        assertEquals(routes + Screen.Settings(), navigateTo(routes, Screen.Settings()))
    }

    @Test
    fun directChildFromAccountsKeepsAccountsAsItsCaller() {
        val routes = listOf(Screen.Profile("author"), Screen.Accounts)
        val child = Screen.Settings(SettingsSection.Notifications)

        assertEquals(routes + Screen.Settings() + child, navigateTo(routes, child))
    }

    @Test
    fun returningToAnExistingThreadPopsSettingsAndPreservesItsReplyFocus() {
        val caller = Screen.Profile("author")
        val thread = Screen.Thread("conversation", focusReplyId = "old-reply")
        val routes = listOf(caller, thread, Screen.Settings(), Screen.Settings(SettingsSection.Server))

        assertEquals(listOf(caller, thread), navigateTo(routes, Screen.Thread(thread.id)))
        val newFocus = Screen.Thread(thread.id, focusReplyId = "new-reply")
        assertEquals(listOf(caller, newFocus), navigateTo(routes, newFocus))
    }

    @Test
    fun aNewThreadAppendsWithoutRewritingTheSettingsBackPath() {
        val child = Screen.Settings(SettingsSection.Server)
        val routes = listOf(Screen.Settings(), child)
        val thread = Screen.Thread("conversation")

        assertEquals(routes + thread, navigateTo(routes, thread))
    }
}
