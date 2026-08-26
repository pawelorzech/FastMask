package com.fastmask.data.billing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guards for requirements that are otherwise hidden behind Play services. */
class PlayBillingApiContractTest {

    private val source = File("src/main/java/com/fastmask/data/billing/PlayBillingDataSource.kt").readText()

    @Test
    fun `ProductDetails are refreshed for purchase instead of cached`() {
        assertFalse(
            "Google warns that cached ProductDetails can make launchBillingFlow fail",
            source.contains("productDetailsCache"),
        )
        val launchBody = source.substringAfter("override suspend fun launchBillingFlow").substringBefore(
            "private fun Purchase.toBillingPurchase",
        )
        assertTrue(launchBody.contains("queryFreshProductDetails(productId)"))
    }

    @Test
    fun `Billing 8 unfetched products are interpreted`() {
        assertTrue(source.contains("result.unfetchedProductList"))
        assertTrue(source.contains("UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND"))
        assertTrue(source.contains("UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER"))
    }
}
