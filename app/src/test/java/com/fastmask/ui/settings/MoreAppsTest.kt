package com.fastmask.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreAppsTest {

    @Test
    fun `the list never offers the app the user is already in`() {
        val own = "com.fastmask"

        val apps = MoreApps.otherThan(own)

        assertTrue(apps.isNotEmpty())
        assertFalse(apps.any { it.packageId == own })
    }

    @Test
    fun `the new apps come last, in order, with an icon and tagline each`() {
        val apps = MoreApps.otherThan("com.fastmask")

        assertEquals(listOf("BibleMe", "TimeNudge"), apps.takeLast(2).map { it.name })
        assertTrue(apps.all { it.iconRes != 0 && it.taglineRes != 0 })
    }
}
