package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.Quote
import com.quanquan.quantoot.model.Status
import kotlin.test.*

class PostContentTest {
    @Test
    fun legacyQuoteLookupUsesAnExplicitClassAndSafeUrl() {
        assertEquals("https://social.example/@author/123?a=1&b=2",
            inlineQuoteUrl("""<a href="https://social.example/@author/123?a=1&amp;b=2" class="mention quote-inline">Quote</a>"""))
        assertNull(inlineQuoteUrl("""<a href="https://example.org" class="not-quote-inline">Link</a>"""))
        assertNull(inlineQuoteUrl("""<a href="javascript:alert(1)" class="quote-inline">Quote</a>"""))
        assertNull(inlineQuoteUrl("""<a data-href="https://example.org" data-class="quote-inline">Link</a>"""))
    }

    @Test
    fun revokedBlockedMutedAndUnknownQuoteStatesCannotRevealEmbeddedContent() {
        for (state in listOf("pending", "rejected", "revoked", "deleted", "unauthorized",
            "blocked_account", "blocked_domain", "muted_account", "unknown", "")) {
            assertFalse(mayResolveQuote(Quote(state, Status(id = "hidden"))), state)
        }
        assertTrue(mayResolveQuote(Quote("accepted", Status(id = "visible"))))
        assertTrue(mayResolveQuote(null))
    }
}
