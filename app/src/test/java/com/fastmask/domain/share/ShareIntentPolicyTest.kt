package com.fastmask.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The three rules that decide whether an incoming intent becomes a prefilled
 * create screen. All three had a bug that made the share target fail SILENTLY —
 * no crash, no message, just the mask list — so each one is pinned here.
 */
class ShareIntentPolicyTest {

    private val send = ShareIntentPolicy.ACTION_SEND

    /**
     * Deliberately NOT a String. `Intent.EXTRA_TEXT` is declared as a
     * CharSequence and senders such as Gmail and Docs put a `Spanned` in it;
     * reading it with `getStringExtra` made `Bundle.getString` swallow a
     * ClassCastException and answer null, killing the whole share path.
     * Spannable is an Android type, so this stands in for it on the JVM.
     */
    private fun styled(text: String): CharSequence = StringBuilder(text)

    @Test
    fun `a styled CharSequence is read like plain text`() {
        assertEquals(
            "https://example.com/signup",
            ShareIntentPolicy.sharedText(send, "text/plain", styled("https://example.com/signup")),
        )
    }

    @Test
    fun `a text subtype other than plain is still a share`() {
        // An IntentFilter declaring text/plain also matches an intent typed
        // "text/*", so an equality check dropped legitimate shares.
        assertEquals("x.example.com", ShareIntentPolicy.sharedText(send, "text/*", "x.example.com"))
        assertEquals("hi", ShareIntentPolicy.sharedText(send, "text/html", "hi"))
    }

    @Test
    fun `non-text and non-send intents are ignored`() {
        assertNull(ShareIntentPolicy.sharedText(send, "image/png", "x"))
        assertNull(ShareIntentPolicy.sharedText(send, null, "x"))
        assertNull(ShareIntentPolicy.sharedText("android.intent.action.VIEW", "text/plain", "x"))
        assertNull(ShareIntentPolicy.sharedText(null, "text/plain", "x"))
    }

    @Test
    fun `missing and blank text yield null`() {
        assertNull(ShareIntentPolicy.sharedText(send, "text/plain", null))
        assertNull(ShareIntentPolicy.sharedText(send, "text/plain", ""))
        assertNull(ShareIntentPolicy.sharedText(send, "text/plain", "   \n\t "))
    }

    @Test
    fun `oversized text is truncated before it can reach the parser`() {
        // Parsing runs on the main thread in onCreate and the share target is
        // exported, so the size of the work must not be the sender's choice.
        val huge = "a".repeat(500_000)

        val text = ShareIntentPolicy.sharedText(send, "text/plain", huge)

        assertEquals(ShareIntentPolicy.MAX_SHARED_TEXT_LENGTH, text?.length)
    }

    @Test
    fun `text within the cap is passed through unchanged`() {
        val shared = "Sign up here: example.com/promo"

        assertEquals(shared, ShareIntentPolicy.sharedText(send, "text/plain", shared))
    }

    @Test
    fun `the action constant matches the platform value`() {
        // Spelled out in the policy so the file stays Android-free; the value
        // is public API and must not drift.
        assertEquals("android.intent.action.SEND", ShareIntentPolicy.ACTION_SEND)
    }
}
