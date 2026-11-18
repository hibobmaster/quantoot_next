package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.Page
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class PeopleListTest {
    @Test
    fun retriesContinuationWithoutLosingRowsOrFollowingACursorTwice() = runTest {
        val list = Screen.People("profile", following = true).list
        val firstPage = (1..20).map { Account(id = it.toString()) }
        list.loadNext {
            assertNull(it)
            Page(firstPage, "next")
        }
        list.loadNext {
            assertEquals("next", it)
            error("offline")
        }
        assertEquals(firstPage, list.people)
        assertEquals("offline", list.error)
        val started = CompletableDeferred<Unit>()
        val next = CompletableDeferred<Page<Account>>()
        val loading = launch {
            list.loadNext {
                assertEquals("next", it)
                started.complete(Unit)
                next.await()
            }
        }
        started.await()
        list.loadNext { fail("Duplicate request") }
        next.complete(Page(listOf(firstPage.last(), Account(id = "21")), "last"))
        loading.join()
        list.loadNext {
            assertEquals("last", it)
            Page(listOf(Account(id = "22")), "next")
        }
        assertEquals((1..22).map(Int::toString), list.people.map { it.id })
        assertTrue(list.exhausted)
        list.loadNext { fail("Cursor cycle") }
    }
}
