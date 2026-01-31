package com.fastmask.ui.navigation

object NavRoutes {
    const val LOGIN = "login"
    const val EMAIL_LIST = "email_list"
    const val CREATE_EMAIL = "create_email"
    const val EMAIL_DETAIL = "email_detail/{emailId}"

    fun emailDetail(emailId: String) = "email_detail/$emailId"
}
