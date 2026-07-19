package com.fastmask.data.analytics

import android.util.Log
import com.fastmask.BuildConfig
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.analytics.MonetizationEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only funnel instrumentation. Logs in debug builds, no-op in release —
 * FastMask ships no telemetry ("no tracking" is a product promise). Conversion
 * and revenue metrics come from Play Console reports instead.
 */
@Singleton
class LogMonetizationAnalytics @Inject constructor() : MonetizationAnalytics {

    override fun track(event: MonetizationEvent, source: String?, detail: String?) {
        if (!BuildConfig.DEBUG) return
        val suffix = buildList {
            source?.let { add("source=$it") }
            detail?.let { add("detail=$it") }
        }.joinToString(" ")
        Log.d(TAG, "${event.name.lowercase()} $suffix".trim())
    }

    private companion object {
        const val TAG = "FastMaskFunnel"
    }
}
