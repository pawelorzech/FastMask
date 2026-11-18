package com.fastmask.domain.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {

    private fun ask(created: Int, asked: Boolean = false, demo: Boolean = false) =
        ReviewPromptPolicy.shouldPrompt(created, asked, demo)

    @Test
    fun `never before the threshold`() {
        assertFalse(ask(0))
        assertFalse(ask(1))
        assertFalse(ask(ReviewPromptPolicy.MIN_MASKS - 1))
    }

    @Test
    fun `asks at the threshold and beyond until asked`() {
        assertTrue(ask(ReviewPromptPolicy.MIN_MASKS))
        assertTrue(ask(ReviewPromptPolicy.MIN_MASKS + 10))
    }

    @Test
    fun `never twice`() {
        assertFalse(ask(ReviewPromptPolicy.MIN_MASKS, asked = true))
        assertFalse(ask(Int.MAX_VALUE, asked = true))
    }

    @Test
    fun `never in demo mode`() {
        assertFalse(ask(ReviewPromptPolicy.MIN_MASKS, demo = true))
        assertFalse(ask(100, demo = true))
    }
}
