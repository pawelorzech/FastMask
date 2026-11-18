package com.fastmask.data.review

import android.app.Activity
import com.fastmask.core.runSuspendCatching
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The Play in-app review flow behind an interface, so the prompter is testable without Play. */
interface ReviewLauncher {
    /**
     * Shows the review card. False when Play declined or is absent (sideload, emulator, quota):
     * the caller must not count that as "asked".
     */
    suspend fun launch(activity: Activity): Boolean
}

@Singleton
class PlayReviewLauncher @Inject constructor() : ReviewLauncher {

    override suspend fun launch(activity: Activity): Boolean = runSuspendCatching {
        val manager = ReviewManagerFactory.create(activity)
        val info = manager.awaitReviewInfo() ?: return@runSuspendCatching false
        manager.awaitReviewFlow(activity, info)
    }.getOrDefault(false)

    // Base `review` artifact, NOT `review-ktx`: the KTX one references a Play services annotation
    // that is not on the classpath and R8 fails the release build on it. Two short wrappers cost
    // less than a -dontwarn.
    private suspend fun ReviewManager.awaitReviewInfo(): ReviewInfo? =
        suspendCancellableCoroutine { continuation ->
            requestReviewFlow().addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }

    // Play does not reveal whether the user rated anything, only that the flow finished.
    private suspend fun ReviewManager.awaitReviewFlow(activity: Activity, info: ReviewInfo): Boolean =
        suspendCancellableCoroutine { continuation ->
            launchReviewFlow(activity, info).addOnCompleteListener { task ->
                continuation.resume(task.isSuccessful)
            }
        }
}
