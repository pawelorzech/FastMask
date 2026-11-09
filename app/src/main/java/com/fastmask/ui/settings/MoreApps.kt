package com.fastmask.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.core.platform.openExternalIntent

/** One of the maker's Play Store apps, as shown in "More from the maker". */
data class MoreApp(
    val name: String,
    val packageId: String,
    @StringRes val taglineRes: Int,
)

/** The maker's catalog, in the order it is listed. */
object MoreApps {

    private val catalog = listOf(
        MoreApp("Nib", "com.nib.microblog", R.string.more_apps_tagline_nib),
        MoreApp("Skim", "com.pawelorzech.skim", R.string.more_apps_tagline_skim),
        MoreApp("Stash", "com.pawelorzech.stash", R.string.more_apps_tagline_stash),
        MoreApp("Paperline", "com.pawelorzech.paperline", R.string.more_apps_tagline_paperline),
        MoreApp("DoteNote", "com.dotenote", R.string.more_apps_tagline_dotenote),
        MoreApp("FastMask", "com.fastmask", R.string.more_apps_tagline_fastmask),
        MoreApp("SunZones", "com.sunzones", R.string.more_apps_tagline_sunzones),
    )

    /** Every app except the one whose package is [ownPackageId]. */
    fun otherThan(ownPackageId: String): List<MoreApp> =
        catalog.filterNot { it.packageId == ownPackageId }

    fun marketUrl(packageId: String): String = "market://details?id=$packageId"

    fun webUrl(packageId: String): String =
        "https://play.google.com/store/apps/details?id=$packageId"

    /** Opens the Play Store app, falling back to the web listing when no store app handles market://. */
    fun open(context: Context, packageId: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl(packageId)))
        if (openExternalIntent(context, market)) return
        openExternalIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(packageId))))
    }
}
