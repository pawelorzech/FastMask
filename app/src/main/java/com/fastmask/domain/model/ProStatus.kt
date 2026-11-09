package com.fastmask.domain.model

/**
 * [PENDING] means Play reported a purchase that is not yet completed (e.g.
 * cash payment): Pro features stay locked until it transitions to [PRO].
 */
enum class ProStatus {
    FREE,
    PENDING,
    PRO;

    val isPro: Boolean get() = this == PRO
}
