package com.fastmask.domain.share

/**
 * Decides whether an incoming intent is a share FastMask should act on, and
 * hands back the text to parse.
 *
 * Split out of `MainActivity` and kept free of Android types so the three rules
 * that used to be one silent `if` are unit-testable:
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

    /**
     * `android.content.Intent.ACTION_SEND`, spelled out so this file stays
     * Android-free. The constant's value is public API and cannot change.
     */
    const val ACTION_SEND: String = "android.intent.action.SEND"

    /** Hard cap on the text handed to [SharedLinkParser]. */
    const val MAX_SHARED_TEXT_LENGTH: Int = 8_192

    /**
     * @param action intent action.
     * @param type intent MIME type.
     * @param text value of `EXTRA_TEXT`, read as a CharSequence.
     * @return the text to parse, truncated to [MAX_SHARED_TEXT_LENGTH], or null
     *   when this intent is not a text share worth acting on.
     */
    fun sharedText(action: String?, type: String?, text: CharSequence?): String? {
        if (action != ACTION_SEND) {
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
