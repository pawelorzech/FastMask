package com.fastmask.domain.usecase

import com.fastmask.domain.hygiene.HygieneBaseline
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

/**
 * The collection as it looked the last time the review was shown.
 *
 * Deliberately NOT [GetCachedMaskedEmailsUseCase]: the mask cache is rewritten
 * by every successful fetch — including the one the list screen fires on each
 * RESUME — so diffing against it always yields nothing. This one is written by
 * the review and by nothing else.
 */
class GetHygieneBaselineUseCase @Inject constructor(
    private val repository: MaskedEmailRepository,
) {
    suspend operator fun invoke(): HygieneBaseline? = repository.hygieneBaseline()
}

/** Records what the user has now seen, so the next review can diff against it. */
class SaveHygieneBaselineUseCase @Inject constructor(
    private val repository: MaskedEmailRepository,
) {
    suspend operator fun invoke(baseline: HygieneBaseline) {
        repository.saveHygieneBaseline(baseline)
    }
}
