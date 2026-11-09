package com.fastmask.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoreAppsTest {

    private val own = "com.fastmask"

    @Test
    fun `list excludes the own package and has six entries`() {
        val apps = MoreApps.otherThan(own)

        assertEquals(6, apps.size)
        assertFalse(apps.any { it.packageId == own })
    }

    @Test
    fun `list keeps the maker's order`() {
        assertEquals(
            listOf("Nib", "Skim", "Stash", "Paperline", "DoteNote", "SunZones"),
            MoreApps.otherThan(own).map { it.name },
        )
    }

    @Test
    fun `store links carry the package id`() {
        assertEquals("market://details?id=com.nib.microblog", MoreApps.marketUrl("com.nib.microblog"))
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.nib.microblog",
            MoreApps.webUrl("com.nib.microblog"),
        )
    }
}
