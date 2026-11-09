package com.fastmask.domain.model

import java.time.Instant

/** Anything showing cached masks has to tell the user how old they are. */
data class CachedMasks(
    val masks: List<MaskedEmail>,
    val cachedAt: Instant,
)
