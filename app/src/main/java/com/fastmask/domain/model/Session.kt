package com.fastmask.domain.model

/**
 * Who the app is working for right now.
 *
 * [DEMO] is a complete local session with in-memory masks: it counts as signed
 * in for navigation, but nothing in it may reach a real Fastmail account.
 */
enum class Session {
    SIGNED_OUT,
    ACCOUNT,
    DEMO;

    val isSignedIn: Boolean get() = this != SIGNED_OUT
    val isDemo: Boolean get() = this == DEMO
}
