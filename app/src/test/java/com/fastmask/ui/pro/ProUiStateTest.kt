package com.fastmask.ui.pro

import com.fastmask.domain.model.ProStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProUiStateTest {

    private fun area(status: ProStatus, product: ProductState) =
        ProUiState(status = status, productState = product).purchaseArea

    @Test
    fun `ownership wins over everything else`() {
        for (product in ProductState.entries) {
            assertEquals(PurchaseArea.OWNED, area(ProStatus.PRO, product))
        }
    }

    @Test
    fun `a pending purchase wins over the product state`() {
        for (product in ProductState.entries) {
            assertEquals(PurchaseArea.PENDING, area(ProStatus.PENDING, product))
        }
    }

    @Test
    fun `a free user follows the product state`() {
        assertEquals(PurchaseArea.LOADING, area(ProStatus.FREE, ProductState.LOADING))
        assertEquals(PurchaseArea.UNAVAILABLE, area(ProStatus.FREE, ProductState.UNAVAILABLE))
        assertEquals(PurchaseArea.READY, area(ProStatus.FREE, ProductState.READY))
    }
}
