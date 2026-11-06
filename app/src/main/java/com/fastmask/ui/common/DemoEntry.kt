package com.fastmask.ui.common

import com.fastmask.R
import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.repository.SessionRepository

/**
 * Enters demo mode and reports how it went, so Welcome and Login share one
 * failure story instead of two copies of it.
 *
 * Returns `null` on success, otherwise the message to show. A dead button is
 * indistinguishable from a broken app, so the caller keeps the user where they
 * are, releases its CTAs and shows this text (the likely cause is a local
 * storage failure, which the message explains how to recover from).
 */
internal suspend fun SessionRepository.enterDemoOrErrorRes(): Int? =
    runSuspendCatching { enterDemo() }.fold(
        onSuccess = { null },
        onFailure = { R.string.demo_error_start },
    )
