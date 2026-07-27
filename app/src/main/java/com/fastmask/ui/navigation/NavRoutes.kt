package com.fastmask.ui.navigation

import android.net.Uri
import com.fastmask.domain.share.SharePrefill

object NavRoutes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val EMAIL_LIST = "email_list"
    const val CREATE_EMAIL =
        "create_email?forDomain={forDomain}&url={url}&description={description}"
    const val EMAIL_DETAIL = "email_detail/{emailId}"
    const val SETTINGS = "settings"
    const val PRO = "pro?source={source}"

    /** Percent-encode values so `/`, `?`, and `&` inside a shared URL do not break the route. */
    fun createEmail(prefill: SharePrefill? = null): String {
        val forDomain: String = Uri.encode(prefill?.forDomain.orEmpty())
        val url: String = Uri.encode(prefill?.url.orEmpty())
        val description: String = Uri.encode(prefill?.description.orEmpty())
        return "create_email?forDomain=$forDomain&url=$url&description=$description"
    }

    fun emailDetail(emailId: String): String = "email_detail/$emailId"

    /** @param source paywall entry point for funnel analytics (no user data). */
    fun pro(source: String): String = "pro?source=$source"
}
