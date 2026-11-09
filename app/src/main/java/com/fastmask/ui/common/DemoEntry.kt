package com.fastmask.ui.common

import com.fastmask.R
import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.repository.SessionRepository

/**
 * Returns `null` on success, otherwise the message to show: a dead button is
 * indistinguishable from a broken app, and the likely cause is a local storage
 * failure, which the message explains how to recover from.
 */
internal suspend fun SessionRepository.enterDemoOrErrorRes(): Int? =
    runSuspendCatching { enterDemo() }.fold(
        onSuccess = { null },
        onFailure = { R.string.demo_error_start },
    )
