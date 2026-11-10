package com.fastmask.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.core.platform.openExternalIntent

data class MoreApp(
    val name: String,
    val packageId: String,
    @StringRes val taglineRes: Int,
    @DrawableRes val iconRes: Int,
)

object MoreApps {

    private val catalog = listOf(
        MoreApp("Nib", "com.nib.microblog", R.string.more_apps_tagline_nib, R.drawable.maker_nib),
        MoreApp("Skim", "com.pawelorzech.skim", R.string.more_apps_tagline_skim, R.drawable.maker_skim),
        MoreApp("Stash", "com.pawelorzech.stash", R.string.more_apps_tagline_stash, R.drawable.maker_stash),
        MoreApp(
            "Paperline", "com.pawelorzech.paperline",
            R.string.more_apps_tagline_paperline, R.drawable.maker_paperline,
        ),
        MoreApp("DoteNote", "com.dotenote", R.string.more_apps_tagline_dotenote, R.drawable.maker_dotenote),
        MoreApp("FastMask", "com.fastmask", R.string.more_apps_tagline_fastmask, R.drawable.maker_fastmask),
        MoreApp("SunZones", "com.sunzones", R.string.more_apps_tagline_sunzones, R.drawable.maker_sunzones),
        MoreApp(
            "BibleMe", "com.doktorbabel.bibleme",
            R.string.more_apps_tagline_bibleme, R.drawable.maker_bibleme,
        ),
        MoreApp("TimeNudge", "com.timenudge", R.string.more_apps_tagline_timenudge, R.drawable.maker_timenudge),
    )

    fun otherThan(ownPackageId: String): List<MoreApp> =
        catalog.filterNot { it.packageId == ownPackageId }

    private fun marketUrl(packageId: String): String = "market://details?id=$packageId"

    private fun webUrl(packageId: String): String =
        "https://play.google.com/store/apps/details?id=$packageId"

    /** Falls back to the web listing when no store app handles market://. */
    fun open(context: Context, packageId: String): Boolean {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl(packageId)))
        if (openExternalIntent(context, market)) return true
        return openExternalIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(packageId))))
    }
}
