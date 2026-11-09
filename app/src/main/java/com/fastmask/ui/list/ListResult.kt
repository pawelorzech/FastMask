package com.fastmask.ui.list

import androidx.lifecycle.SavedStateHandle
import com.fastmask.domain.model.EmailState
import kotlinx.coroutines.flow.StateFlow
import java.io.Serializable
import java.util.UUID

/** Serializable so it survives process death, in the handle and in the list's `rememberSaveable`. */
sealed interface ListResult : Serializable {
    /** [delivery] makes two archives of the same mask distinct values. */
    data class Archived(
        val id: String,
        val previousState: EmailState,
        val delivery: String = UUID.randomUUID().toString(),
    ) : ListResult

    data class Created(val email: String) : ListResult
}

private const val LIST_RESULT_KEY = "list_result"

fun SavedStateHandle.postListResult(result: ListResult) {
    this[LIST_RESULT_KEY] = result
}

fun SavedStateHandle.pendingListResult(): StateFlow<ListResult?> =
    getStateFlow<ListResult?>(LIST_RESULT_KEY, null)

fun SavedStateHandle.consumeListResult(result: ListResult) {
    if (get<ListResult>(LIST_RESULT_KEY) == result) this[LIST_RESULT_KEY] = null
}
