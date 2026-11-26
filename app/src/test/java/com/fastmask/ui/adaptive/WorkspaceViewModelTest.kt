package com.fastmask.ui.adaptive

import androidx.lifecycle.SavedStateHandle
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.list.ListResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceViewModelTest {

    @Test
    fun `selection and create pane survive recreation via SavedStateHandle`() {
        val handle = SavedStateHandle()
        val first = WorkspaceViewModel(handle)
        assertEquals(WorkspaceDetailPane.None, first.detailPane.value)
        assertNull(first.selectedEmailId)

        first.selectEmail("mask-42")
        assertEquals(WorkspaceDetailPane.Detail("mask-42"), first.detailPane.value)
        assertEquals("mask-42", first.selectedEmailId)

        val restoredDetail = WorkspaceViewModel(handle)
        assertEquals(WorkspaceDetailPane.Detail("mask-42"), restoredDetail.detailPane.value)

        restoredDetail.openCreate("https://example.com")
        val restoredCreate = WorkspaceViewModel(handle)
        assertEquals(WorkspaceDetailPane.Create("https://example.com"), restoredCreate.detailPane.value)
        assertNull(restoredCreate.selectedEmailId)

        restoredCreate.clearDetail()
        val restoredEmpty = WorkspaceViewModel(handle)
        assertEquals(WorkspaceDetailPane.None, restoredEmpty.detailPane.value)
    }

    @Test
    fun `creating a mask from detail pane selects it and posts list result`() {
        val vm = WorkspaceViewModel(SavedStateHandle())
        vm.openCreate()

        vm.onMaskCreated(id = "m-new", email = "fresh.mask@fastmail.com")

        assertEquals(WorkspaceDetailPane.Detail("m-new"), vm.detailPane.value)
        assertEquals(ListResult.Created("fresh.mask@fastmail.com"), vm.pendingListResult.value)

        vm.consumeListResult(ListResult.Created("fresh.mask@fastmail.com"))
        assertNull(vm.pendingListResult.value)
    }

    @Test
    fun `archiving selected mask posts undo result and clears detail pane`() {
        val vm = WorkspaceViewModel(SavedStateHandle())
        vm.selectEmail("m-1")

        vm.onMaskArchived(id = "m-1", previousState = EmailState.DISABLED)

        assertEquals(WorkspaceDetailPane.None, vm.detailPane.value)
        val result = vm.pendingListResult.value
        assertTrue(result is ListResult.Archived)
        val archived = result as ListResult.Archived
        assertEquals("m-1", archived.id)
        assertEquals(EmailState.DISABLED, archived.previousState)
    }
}
