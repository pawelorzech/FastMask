package com.fastmask.ui.list

import androidx.lifecycle.SavedStateHandle
import com.fastmask.domain.model.EmailState
import java.io.Serializable

/**
 * What a screen opened from the list hands back to it, as one typed value in the
 * list entry's [SavedStateHandle]. Serializable so it survives rotation and
 * process death, both in the handle and in the list's own `rememberSaveable`.
 */
sealed interface ListResult : Serializable {
    /** The detail screen archived mask [id], which was in [previousState] before. */
    data class Archived(val id: String, val previousState: EmailState) : ListResult

    /** The create screen made the mask with this [email] address. */
    data class Created(val email: String) : ListResult

    companion object {
        const val KEY = "list_result"
    }
}

/** Hands [result] to the list, replacing any result it has not consumed yet. */
fun SavedStateHandle.postListResult(result: ListResult) {
    this[ListResult.KEY] = result
}

/**
 * Marks [result] as shown. Clears the slot only while it still holds exactly
 * [result]: a newer result that arrived while this one was on screen (a second
 * archive during the first Undo) must never be erased by consuming the older one.
 */
fun SavedStateHandle.consumeListResult(result: ListResult) {
    if (get<ListResult>(ListResult.KEY) == result) this[ListResult.KEY] = null
}
