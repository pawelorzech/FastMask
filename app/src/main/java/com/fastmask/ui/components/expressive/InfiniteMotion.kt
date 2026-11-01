package com.fastmask.ui.components.expressive

import androidx.compose.ui.platform.InfiniteAnimationPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

/**
 * `delay` for the hold phase of a never-ending animation loop. It runs through
 * [InfiniteAnimationPolicy], exactly like `withInfiniteAnimationFrameMillis` and
 * `rememberInfiniteTransition` do, so a test (or any host that installs a policy)
 * can cancel the loop instead of waiting for it to go idle forever.
 */
suspend fun delayInfinite(timeMillis: Long) {
    val policy = currentCoroutineContext()[InfiniteAnimationPolicy]
    if (policy != null) policy.onInfiniteOperation { delay(timeMillis) } else delay(timeMillis)
}
