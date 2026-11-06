package com.fastmask.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import com.fastmask.domain.share.SharePrefill

object NavRoutes {
    /** Argument names, shared by the route templates and the ViewModels that read them. */
    const val ARG_EMAIL_ID = "emailId"
    const val ARG_FOR_DOMAIN = "forDomain"
    const val ARG_SOURCE = "source"

    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val EMAIL_LIST = "email_list"
    const val CREATE_EMAIL = "create_email?$ARG_FOR_DOMAIN={$ARG_FOR_DOMAIN}"
    const val EMAIL_DETAIL = "email_detail/{$ARG_EMAIL_ID}"
    const val SETTINGS = "settings"
    const val PRO = "pro?$ARG_SOURCE={$ARG_SOURCE}"

    /** Percent-encoded so `/`, `?` and `&` inside a shared origin do not break the route. */
    fun createEmail(prefill: SharePrefill? = null): String =
        "create_email?$ARG_FOR_DOMAIN=${Uri.encode(prefill?.forDomain.orEmpty())}"

    fun emailDetail(emailId: String): String = "email_detail/$emailId"

    /** @param source paywall entry point for funnel analytics (no user data). */
    fun pro(source: String): String = "pro?$ARG_SOURCE=$source"
}

/** Opens [route] as the only screen, so back leaves the app instead of returning to a previous session. */
fun NavController.resetTo(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
