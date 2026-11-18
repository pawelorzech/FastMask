package com.fastmask.data.review

import android.app.Activity
import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.domain.review.ReviewPromptPolicy
import com.fastmask.domain.review.ReviewTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts created masks, applies [ReviewPromptPolicy] and raises [due]. The Activity owns the
 * actual launch (it knows about the lock screen and dialogs) and calls [launch] when it is
 * safe. Nothing here throws: a broken DataStore must not break creating a mask.
 */
@Singleton
class ReviewPrompter @Inject constructor(
    private val preferences: ReviewPreferences,
    private val sessionRepository: SessionRepository,
    private val launcher: ReviewLauncher,
) : ReviewTrigger {

    private val _due = MutableStateFlow(false)

    /** True when a success moment has earned the prompt and it has not been shown yet. */
    val due: StateFlow<Boolean> = _due.asStateFlow()

    override suspend fun onMaskCreated() {
        runSuspendCatching {
            val isDemo = sessionRepository.current().isDemo
            // Demo masks are not counted at all: they must not pre-arm the prompt for a real account.
            if (isDemo || preferences.alreadyAsked()) return@runSuspendCatching
            val created = preferences.recordMaskCreated()
            if (ReviewPromptPolicy.shouldPrompt(created, alreadyAsked = false, isDemo = false)) {
                _due.value = true
            }
        }
    }

    /**
     * Shows the card and latches "asked" only if Play really showed it, so a device without
     * Play stays eligible for the day it gets one.
     */
    suspend fun launch(activity: Activity) {
        _due.value = false
        if (launcher.launch(activity)) {
            runSuspendCatching { preferences.markAsked() }
        }
    }
}
