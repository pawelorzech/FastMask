package com.fastmask.domain.analytics

/**
 * Monetization funnel events.
 *
 * FastMask promises "no tracking" (Play listing, privacy policy), so there is
 * deliberately NO third-party analytics SDK behind this interface. The default
 * implementation logs locally in debug builds only; conversion and revenue are
 * measured through Play Console's built-in reports. The interface still exists
 * so every funnel point is instrumented at the call site — if an opt-in,
 * privacy-respecting sink is ever added, no feature code changes.
 */
interface MonetizationAnalytics {

    /**
     * @param source where the event originated (e.g. "settings", "accent",
     *   "app_lock", "export") — never user data.
     * @param detail coarse machine detail (e.g. an error code name) —
     *   never purchase tokens, order ids, or personal data.
     */
    fun track(event: MonetizationEvent, source: String? = null, detail: String? = null)
}

enum class MonetizationEvent {
    PAYWALL_VIEWED,
    PAYWALL_CLOSED,
    PREMIUM_FEATURE_TAPPED,
    PURCHASE_STARTED,
    PURCHASE_COMPLETED,
    PURCHASE_CANCELLED,
    PURCHASE_PENDING,
    PURCHASE_FAILED,
    PURCHASE_RESTORED,
    ENTITLEMENT_ACTIVATED,
    ENTITLEMENT_EXPIRED,
}
