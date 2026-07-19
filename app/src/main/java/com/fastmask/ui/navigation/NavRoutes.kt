package com.fastmask.ui.navigation

object NavRoutes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val EMAIL_LIST = "email_list"
    const val CREATE_EMAIL = "create_email"
    const val EMAIL_DETAIL = "email_detail/{emailId}"
    const val SETTINGS = "settings"
    const val PRO = "pro?source={source}"

    fun emailDetail(emailId: String) = "email_detail/$emailId"

    /** @param source paywall entry point for funnel analytics (no user data). */
    fun pro(source: String) = "pro?source=$source"
}
