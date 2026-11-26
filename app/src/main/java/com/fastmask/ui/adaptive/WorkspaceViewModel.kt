package com.fastmask.ui.adaptive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.list.ListResult
import com.fastmask.ui.list.consumeListResult
import com.fastmask.ui.list.pendingListResult
import com.fastmask.ui.list.postListResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface WorkspaceDetailPane {
    data object None : WorkspaceDetailPane
    data class Detail(val emailId: String) : WorkspaceDetailPane
    data class Create(val forDomain: String = "") : WorkspaceDetailPane
}

/**
 * Persists the two-pane workspace selection (open mask detail or create form) and shared
 * list results across fold/unfold, rotation, and process death.
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _detailPane = MutableStateFlow(restoreDetailPane(savedStateHandle))
    val detailPane: StateFlow<WorkspaceDetailPane> = _detailPane.asStateFlow()

    val pendingListResult: StateFlow<ListResult?> = savedStateHandle.pendingListResult()

    val selectedEmailId: String?
        get() = (_detailPane.value as? WorkspaceDetailPane.Detail)?.emailId

    fun selectEmail(emailId: String) {
        savedStateHandle[KEY_MODE] = MODE_DETAIL
        savedStateHandle[KEY_EMAIL_ID] = emailId
        savedStateHandle.remove<String>(KEY_FOR_DOMAIN)
        _detailPane.value = WorkspaceDetailPane.Detail(emailId)
    }

    fun openCreate(forDomain: String = "") {
        savedStateHandle[KEY_MODE] = MODE_CREATE
        savedStateHandle.remove<String>(KEY_EMAIL_ID)
        savedStateHandle[KEY_FOR_DOMAIN] = forDomain
        _detailPane.value = WorkspaceDetailPane.Create(forDomain)
    }

    fun clearDetail() {
        savedStateHandle[KEY_MODE] = MODE_NONE
        savedStateHandle.remove<String>(KEY_EMAIL_ID)
        savedStateHandle.remove<String>(KEY_FOR_DOMAIN)
        _detailPane.value = WorkspaceDetailPane.None
    }

    fun onMaskCreated(id: String, email: String) {
        savedStateHandle.postListResult(ListResult.Created(email))
        if (id.isNotBlank()) {
            selectEmail(id)
        } else {
            clearDetail()
        }
    }

    fun onMaskArchived(id: String, previousState: EmailState) {
        savedStateHandle.postListResult(ListResult.Archived(id, previousState))
        if (selectedEmailId == id) {
            clearDetail()
        }
    }

    fun postListResult(result: ListResult) {
        savedStateHandle.postListResult(result)
    }

    fun consumeListResult(result: ListResult) {
        savedStateHandle.consumeListResult(result)
    }

    private companion object {
        const val KEY_MODE = "ws_detail_mode"
        const val KEY_EMAIL_ID = "ws_detail_email_id"
        const val KEY_FOR_DOMAIN = "ws_create_for_domain"

        const val MODE_NONE = "none"
        const val MODE_DETAIL = "detail"
        const val MODE_CREATE = "create"

        fun restoreDetailPane(handle: SavedStateHandle): WorkspaceDetailPane =
            when (handle.get<String>(KEY_MODE)) {
                MODE_DETAIL -> handle.get<String>(KEY_EMAIL_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { WorkspaceDetailPane.Detail(it) }
                    ?: WorkspaceDetailPane.None
                MODE_CREATE -> WorkspaceDetailPane.Create(
                    forDomain = handle.get<String>(KEY_FOR_DOMAIN).orEmpty(),
                )
                else -> WorkspaceDetailPane.None
            }
    }
}
