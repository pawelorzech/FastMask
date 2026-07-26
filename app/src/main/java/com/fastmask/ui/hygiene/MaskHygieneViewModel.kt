package com.fastmask.ui.hygiene

import androidx.lifecycle.ViewModel
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.hygiene.HygieneIssue
import com.fastmask.domain.hygiene.MaskHygieneReport
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.repository.ProRepository
import com.fastmask.domain.usecase.DeleteMaskedEmailUseCase
import com.fastmask.domain.usecase.GetCachedMaskedEmailsUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Clock

/**
 * STUB — contract only. No behaviour, so `MaskHygieneViewModelTest` fails red.
 *
 * Hilt wiring is deliberately left off: `@HiltViewModel` here would need
 * bindings for [Clock] and the compute dispatcher before the annotation
 * processor is happy, and that module is the implementer's job.
 */
class MaskHygieneViewModel(
    private val getMaskedEmailsUseCase: GetMaskedEmailsUseCase,
    private val getCachedMaskedEmailsUseCase: GetCachedMaskedEmailsUseCase,
    private val updateMaskedEmailUseCase: UpdateMaskedEmailUseCase,
    private val deleteMaskedEmailUseCase: DeleteMaskedEmailUseCase,
    private val proRepository: ProRepository,
    private val analytics: MonetizationAnalytics,
    private val clock: Clock,
    private val computeDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaskHygieneUiState())
    val uiState: StateFlow<MaskHygieneUiState> = _uiState.asStateFlow()

    val events: Flow<MaskHygieneEvent> = emptyFlow()

    /** User-initiated reload. The real implementation also runs this from `init`. */
    fun refresh(): Unit = TODO("mask hygiene review not implemented")

    fun onMaskToggled(id: String): Unit = TODO("selection not implemented")

    fun onSelectAll(issue: HygieneIssue): Unit = TODO("selection not implemented")

    fun onClearSelection(): Unit = TODO("selection not implemented")

    fun onBulkAction(action: BulkAction): Unit = TODO("bulk actions not implemented")

    fun undoBulkAction(result: BulkActionResult): Unit = TODO("bulk undo not implemented")

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
