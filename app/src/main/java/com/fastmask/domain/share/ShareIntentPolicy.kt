package com.fastmask.domain.share

/**
 * 1. `EXTRA_TEXT` is declared as a **CharSequence**. Senders that put a
 *    `Spanned` in it (Gmail, Docs, several readers) made `getStringExtra`
 *    return null — `Bundle.getString` swallows the ClassCastException — so the
 *    whole share path died silently. The caller must read the extra with
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

    const val MAX_SHARED_TEXT_LENGTH: Int = 8_192

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
        val end: Int = minOf(text.length, MAX_SHARED_TEXT_LENGTH)
        return text.subSequence(0, end).toString()
    }
}
