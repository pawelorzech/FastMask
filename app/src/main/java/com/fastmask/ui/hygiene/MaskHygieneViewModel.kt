package com.fastmask.ui.hygiene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.di.ApplicationScope
import com.fastmask.di.DefaultDispatcher
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.hygiene.HygieneBaseline
import com.fastmask.domain.hygiene.HygieneIssue
import com.fastmask.domain.hygiene.MaskHygiene
import com.fastmask.domain.hygiene.MaskHygieneReport
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.usecase.DeleteMaskedEmailUseCase
import com.fastmask.domain.usecase.GetHygieneBaselineUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.SaveHygieneBaselineUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

/**
 * Reviews the current mask list against the review's own retained baseline and
 * owns the sequential bulk actions that clean it up.
 */
@HiltViewModel
class MaskHygieneViewModel @Inject constructor(
    private val getMaskedEmailsUseCase: GetMaskedEmailsUseCase,
    private val getHygieneBaselineUseCase: GetHygieneBaselineUseCase,
    private val saveHygieneBaselineUseCase: SaveHygieneBaselineUseCase,
    private val updateMaskedEmailUseCase: UpdateMaskedEmailUseCase,
    private val deleteMaskedEmailUseCase: DeleteMaskedEmailUseCase,
    private val proRepository: ProRepository,
    private val analytics: MonetizationAnalytics,
    private val clock: Clock,
    @DefaultDispatcher
    private val computeDispatcher: CoroutineDispatcher,
    /**
     * Bulk runs live here rather than in `viewModelScope`. A sequential run is
     * N separate account mutations; if the screen going away killed it midway,
     * the user would be left with an unknown number of masks changed, no count
     * and no undo. See [onBulkAction].
     */
    @ApplicationScope
    private val externalScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaskHygieneUiState())
    val uiState: StateFlow<MaskHygieneUiState> = _uiState.asStateFlow()

    // Buffered one-shot events survive a cold start collector so the paywall
    // still opens when the screen begins collecting after init.
    private val _events = Channel<MaskHygieneEvent>(Channel.BUFFERED)
    val events: Flow<MaskHygieneEvent> = _events.receiveAsFlow()

    /**
     * Read exactly once per visit, before the first fetch. Kept in memory for
     * the rest of the visit so an in-screen refresh (or the reload after a bulk
     * action) still diffs against what the user was originally shown, rather
     * than against the baseline this visit has since written.
     */
    private var retainedBaseline: HygieneBaseline? = null
    private var hasReadBaseline: Boolean = false
    private var loadedMasks: List<MaskedEmail> = emptyList()

    init {
        refresh()
        observeEntitlement()
    }

    /**
     * The entitlement is a flow, not a fact read once.
     *
     * Without this, the free user who bounces off the paywall, buys Pro and
     * pops back lands on the SAME ViewModel instance — still holding the empty
     * report the gate left behind, with no refresh affordance anywhere on the
     * screen. They would have paid and be looking at "no masks yet".
     */
    private fun observeEntitlement(): Unit {
        viewModelScope.launch {
            proRepository.proStatus
                .map { status -> status.isPro }
                .distinctUntilChanged()
                // The current value is the one `refresh()` already acted on.
                .drop(1)
                .collect { isPro ->
                    if (isPro) {
                        loadReview(requirePro = false)
                    }
                }
        }
    }

    /** User-initiated reload. Entry remains Pro-gated, exactly like first open. */
    fun refresh(): Unit {
        viewModelScope.launch {
            loadReview(requirePro = true)
        }
    }

    fun onMaskToggled(id: String): Unit {
        _uiState.update { state ->
            val selectedIds: Set<String> = if (id in state.selectedIds) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = selectedIds)
        }
    }

    fun onSelectAll(issue: HygieneIssue): Unit {
        val idsForIssue: Set<String> = _uiState.value.report.masksFor(issue)
            .map { mask -> mask.id }
            .toSet()
        _uiState.update { state -> state.copy(selectedIds = state.selectedIds + idsForIssue) }
    }

    /**
     * Ignored while a run is in flight. The selection is what the run is
     * operating on, and the system back gesture reaches this without passing
     * the button's own `enabled` guard — clearing it mid-run would tell the
     * user nothing is selected while masks are still being changed.
     */
    fun onClearSelection(): Unit {
        if (_uiState.value.actionInFlight) return
        _uiState.update { state -> state.copy(selectedIds = emptySet()) }
    }

    /**
     * The in-flight flag is set BEFORE the coroutine launch so two taps in one
     * frame cannot both start a sequential run against the account.
     */
    fun onBulkAction(action: BulkAction): Unit {
        val selectedIds: Set<String> = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return
        if (_uiState.value.actionInFlight) return

        _uiState.update { state -> state.copy(actionInFlight = true, errorRes = null) }

        if (!proRepository.proStatus.value.isPro) {
            analytics.track(MonetizationEvent.PREMIUM_FEATURE_TAPPED, source = PAYWALL_SOURCE)
            _uiState.update { state -> state.copy(actionInFlight = false) }
            viewModelScope.launch {
                _events.send(MaskHygieneEvent.OpenPro(PAYWALL_SOURCE))
            }
            return
        }

        val targets: List<MaskedEmail> = loadedMasks.filter { mask -> mask.id in selectedIds }
        if (targets.isEmpty()) {
            _uiState.update { state -> state.copy(actionInFlight = false) }
            return
        }

        externalScope.launch {
            try {
                val succeeded: MutableList<MaskUndoState> = mutableListOf()
                val failedIds: MutableList<String> = mutableListOf()

                targets.forEach { mask ->
                    val result: Result<Unit> = when (action) {
                        BulkAction.DISABLE -> updateMaskedEmailUseCase(
                            mask.id,
                            UpdateMaskedEmailParams(state = EmailState.DISABLED),
                        )
                        BulkAction.ARCHIVE -> deleteMaskedEmailUseCase(mask.id)
                    }
                    result.fold(
                        onSuccess = {
                            succeeded += MaskUndoState(id = mask.id, previousState = mask.state)
                        },
                        onFailure = {
                            failedIds += mask.id
                        },
                    )
                }

                val bulkResult = BulkActionResult(
                    action = action,
                    succeeded = succeeded,
                    failedIds = failedIds,
                )
                // Buffered channel, so the outcome is recorded even if the
                // screen collecting it is already gone.
                _events.send(MaskHygieneEvent.BulkActionFinished(bulkResult))
                _uiState.update { state -> state.copy(selectedIds = failedIds.toSet()) }
                loadReview(requirePro = false)
            } finally {
                _uiState.update { state -> state.copy(actionInFlight = false) }
            }
        }
    }

    fun undoBulkAction(result: BulkActionResult): Unit {
        externalScope.launch {
            var firstFailure: Throwable? = null

            result.succeeded.forEach { undoState ->
                updateMaskedEmailUseCase(
                    undoState.id,
                    UpdateMaskedEmailParams(state = undoState.previousState),
                ).fold(
                    onSuccess = {
                        Unit
                    },
                    onFailure = { error ->
                        // Undo stays best-effort per mask so one failure does
                        // not stop the rest from being restored.
                        if (firstFailure == null) {
                            firstFailure = error
                        }
                    },
                )
            }

            loadReview(requirePro = false)

            val undoFailure: Throwable? = firstFailure
            if (undoFailure != null) {
                _uiState.update { state ->
                    state.copy(
                        errorRes = UiErrors.messageRes(
                            undoFailure,
                            R.string.email_detail_error_update,
                        )
                    )
                }
            }
        }
    }

    /**
     * `requirePro` is only for entry/refresh. Reloads after a completed action
     * must still render the server truth for the screen already in front of the
     * user, rather than abruptly replacing it with a paywall state.
     */
    private suspend fun loadReview(requirePro: Boolean): Unit {
        if (requirePro && !proRepository.proStatus.value.isPro) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    report = MaskHygieneReport.EMPTY,
                    errorRes = null,
                )
            }
            analytics.track(MonetizationEvent.PREMIUM_FEATURE_TAPPED, source = PAYWALL_SOURCE)
            _events.send(MaskHygieneEvent.OpenPro(PAYWALL_SOURCE))
            return
        }

        _uiState.update { state -> state.copy(isLoading = true, errorRes = null) }

        if (!hasReadBaseline) {
            retainedBaseline = getHygieneBaselineUseCase()
            hasReadBaseline = true
        }

        getMaskedEmailsUseCase().fold(
            onSuccess = { masks ->
                loadedMasks = masks
                val report: MaskHygieneReport = withContext(computeDispatcher) {
                    MaskHygiene.review(
                        masks = masks,
                        now = clock.instant(),
                        baseline = retainedBaseline,
                    )
                }
                val loadedIds: Set<String> = masks.map { mask -> mask.id }.toSet()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        report = report,
                        selectedIds = state.selectedIds.intersect(loadedIds),
                        errorRes = null,
                    )
                }
                // Written AFTER the report is computed, and only on a fetch we
                // actually rendered: the user has now seen this state, so it is
                // what the next visit must diff against. Persisting it here
                // rather than on close keeps it correct through a crash or a
                // process death, which "on close" cannot promise.
                saveHygieneBaselineUseCase(
                    HygieneBaseline.of(masks = masks, reviewedAt = clock.instant()),
                )
            },
            onFailure = { error ->
                // A failed refresh must not take the report away. The list
                // screen makes the same promise for the same reason: the masks
                // on screen are still the truth we last saw, and after a
                // partial bulk run the surviving selection IS the retry list.
                val hasData: Boolean = loadedMasks.isNotEmpty()
                _uiState.update { state ->
                    if (hasData) {
                        state.copy(
                            isLoading = false,
                            errorRes = UiErrors.messageRes(error, R.string.error_load_emails),
                        )
                    } else {
                        state.copy(
                            isLoading = false,
                            report = MaskHygieneReport.EMPTY,
                            selectedIds = emptySet(),
                            errorRes = UiErrors.messageRes(error, R.string.error_load_emails),
                        )
                    }
                }
            },
        )
    }

    companion object {
        /** Paywall funnel source for this feature — no user data. */
        const val PAYWALL_SOURCE = "hygiene"
    }
}

data class MaskHygieneUiState(
    val isLoading: Boolean = false,
    val report: MaskHygieneReport = MaskHygieneReport.EMPTY,
    val selectedIds: Set<String> = emptySet(),
    /** True while a bulk run is in flight — also the double-tap guard. */
    val actionInFlight: Boolean = false,
    val errorRes: Int? = null,
)

/** Destructive-by-degrees, and deliberately no bulk delete. */
enum class BulkAction { DISABLE, ARCHIVE }

/** What a mask looked like before a bulk action, so undo can put it back. */
data class MaskUndoState(val id: String, val previousState: EmailState)

/**
 * The honest outcome of a sequential bulk run: what worked, what did not.
 * A partially failed run must never be presented as a success.
 */
data class BulkActionResult(
    val action: BulkAction,
    val succeeded: List<MaskUndoState> = emptyList(),
    val failedIds: List<String> = emptyList(),
) {
    val requested: Int get() = succeeded.size + failedIds.size
    val isCompleteSuccess: Boolean get() = failedIds.isEmpty() && succeeded.isNotEmpty()
    val isPartial: Boolean get() = succeeded.isNotEmpty() && failedIds.isNotEmpty()
}

sealed class MaskHygieneEvent {
    /** @param source paywall funnel entry point, never user data. */
    data class OpenPro(val source: String) : MaskHygieneEvent()

    data class BulkActionFinished(val result: BulkActionResult) : MaskHygieneEvent()
}
