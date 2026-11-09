package com.fastmask.domain.share

/**
 * Decides whether an incoming intent is a share FastMask should act on, and
 * hands back the text to parse.
 *
 * Kept free of Android types so the three rules below are unit-testable:
 *
 * 1. `EXTRA_TEXT` is declared as a **CharSequence**. Senders that put a
 *    `Spanned` in it (Gmail, Docs, several readers) made `getStringExtra`
 *    return null — `Bundle.getString` swallows the ClassCastException — so the
 *    whole share path died silently. The parameter type here is CharSequence
 *    for exactly that reason; the caller must read the extra with
 *    `getCharSequenceExtra`.
 * 2. An `IntentFilter` declaring `text/plain` also matches an intent typed
 *    with a `text` wildcard subtype, so an equality check on the type dropped
 *    legitimate shares.
 * 3. The text is truncated before it ever reaches the parser. Parsing runs on
 *    the main thread in `onCreate`, the share target is exported, and nothing
 *    stops a sender from attaching hundreds of kilobytes; a link that matters
 *    is never past the first few kilobytes of a share.
 */
object ShareIntentPolicy {

    /** Hard cap on the text handed to [SharedLinkParser]. */
    const val MAX_SHARED_TEXT_LENGTH: Int = 8_192

    /**
     * @param isSend whether the intent's action is `Intent.ACTION_SEND`; the
     *   Android-side caller answers that, so this file needs no Android types.
     * @param type intent MIME type.
     * @param text value of `EXTRA_TEXT`, read as a CharSequence.
     * @return the text to parse, truncated to [MAX_SHARED_TEXT_LENGTH], or null
     *   when this intent is not a text share worth acting on.
     */
    fun sharedText(isSend: Boolean, type: String?, text: CharSequence?): String? {
        if (!isSend) {
            return null
        }
        if (type?.startsWith("text/") != true) {
            return null
        }
        if (text == null || text.isBlank()) {
            return null
        }
        // subSequence before toString: a 300k-char share is never copied whole.
        val end: Int = minOf(text.length, MAX_SHARED_TEXT_LENGTH)
        return text.subSequence(0, end).toString()
    }
}
