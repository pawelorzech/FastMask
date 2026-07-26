package com.fastmask.domain.hygiene

import com.fastmask.domain.model.MaskedEmail
import java.time.Instant

/**
 * What the collection looked like the last time the user reviewed it.
 *
 * Deliberately NOT the mask cache. `MaskedEmailRepositoryImpl.getMaskedEmails()`
 * write-throughs that cache on every successful fetch, and the list refreshes on
 * every RESUME — so by the time the user opens this screen the cache already
 * equals the server state and a diff against it is always empty. The review
 * keeps its own retention instead: written when the review is shown, read on the
 * next visit, and untouched by any list refresh in between.
 *
 * @param reviewedAt when this baseline was taken.
 * @param lastMessageAtById id → last message instant AT REVIEW TIME. A null
 *   VALUE means "this mask existed and had never received anything"; an ABSENT
 *   key means the mask did not exist yet, which is a different fact — a mask the
 *   user just created is not a mask that started receiving mail.
 */
data class HygieneBaseline(
    val reviewedAt: Instant,
    val lastMessageAtById: Map<String, Instant?>,
) {
    companion object {
        /** The baseline to persist for a collection the user is looking at now. */
        fun of(masks: List<MaskedEmail>, reviewedAt: Instant): HygieneBaseline = HygieneBaseline(
            reviewedAt = reviewedAt,
            lastMessageAtById = masks.associate { mask -> mask.id to mask.lastMessageAt },
        )
    }
}
