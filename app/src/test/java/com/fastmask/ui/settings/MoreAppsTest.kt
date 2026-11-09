package com.fastmask.ui.settings

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
}
