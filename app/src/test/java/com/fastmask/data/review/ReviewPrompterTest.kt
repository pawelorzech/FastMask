package com.fastmask.data.review

import android.app.Activity
import com.fastmask.domain.model.Session
import com.fastmask.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPrompterTest {

    private var created = 0
    private var asked = false
    private val preferences = mockk<ReviewPreferences> {
        coEvery { alreadyAsked() } answers { asked }
        coEvery { recordMaskCreated() } answers { ++created }
        coEvery { markAsked() } answers { asked = true }
    }

    private val session = mockk<SessionRepository>()
    private var shown = true
    private val launcher = object : ReviewLauncher {
        override suspend fun launch(activity: Activity): Boolean = shown
    }

    private fun prompter(demo: Boolean = false): ReviewPrompter {
        coEvery { session.current() } returns if (demo) Session.DEMO else Session.ACCOUNT
        return ReviewPrompter(preferences, session, launcher)
    }

    @Test
    fun `due only after the third created mask`() = runTest {
        val p = prompter()
        p.onMaskCreated(); p.onMaskCreated()
        assertFalse(p.due.value)
        p.onMaskCreated()
        assertTrue(p.due.value)
    }

    @Test
    fun `demo masks are not counted`() = runTest {
        val p = prompter(demo = true)
        repeat(5) { p.onMaskCreated() }
        assertFalse(p.due.value)
        coVerify(exactly = 0) { preferences.recordMaskCreated() }
    }

    @Test
    fun `a shown card is latched and never asked again`() = runTest {
        val p = prompter()
        repeat(3) { p.onMaskCreated() }
        p.launch(mockk(relaxed = true))
        assertTrue(asked)
        assertFalse(p.due.value)
        p.onMaskCreated()
        assertFalse(p.due.value)
    }

    @Test
    fun `a card Play refused is not latched`() = runTest {
        shown = false
        val p = prompter()
        repeat(3) { p.onMaskCreated() }
        p.launch(mockk(relaxed = true))
        assertFalse(asked)
        p.onMaskCreated()
        assertTrue(p.due.value)
    }
}
