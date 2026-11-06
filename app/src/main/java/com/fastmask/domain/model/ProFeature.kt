package com.fastmask.domain.model

/** What FastMask Pro unlocks. [source] labels a tap on the locked feature for analytics and the paywall. */
enum class ProFeature(val source: String) {
    ACCENT("accent"),
    APP_LOCK("app_lock"),
    EXPORT("export"),
}
