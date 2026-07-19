package com.fastmask.domain.model

/**
 * Entitlement state for FastMask Pro (one-time `pro_lifetime` purchase).
 *
 * The authoritative source is Google Play (`queryPurchasesAsync`); the last
 * verified state is cached locally so the app behaves sensibly offline.
 * [PENDING] means Play reported a purchase that is not yet completed (e.g.
 * cash payment) — Pro features stay locked until it transitions to [PRO].
 */
enum class ProStatus {
    FREE,
    PENDING,
    PRO;

    val isPro: Boolean get() = this == PRO
}
