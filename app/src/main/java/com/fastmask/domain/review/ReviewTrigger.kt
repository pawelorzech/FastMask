package com.fastmask.domain.review

/** Told about a success moment. What, if anything, happens next is not the caller's business. */
interface ReviewTrigger {
    suspend fun onMaskCreated()
}
