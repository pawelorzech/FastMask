package com.fastmask.domain.model

import java.time.Instant

/**
 * A snapshot of the mask list together with when it was taken.
 *
 * The timestamp is part of the model, not an implementation detail: anything
 * showing cached masks has to tell the user how old they are, and carrying the
 * two together makes that hard to forget.
 */
data class CachedMasks(
    val masks: List<MaskedEmail>,
    val cachedAt: Instant,
)
