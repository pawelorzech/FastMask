package com.fastmask.domain.model

/** [DEMO] counts as signed in for navigation, but nothing in it may reach a real Fastmail account. */
enum class Session {
    SIGNED_OUT,
    ACCOUNT,
    DEMO;

    val isSignedIn: Boolean get() = this != SIGNED_OUT
    val isDemo: Boolean get() = this == DEMO
}
