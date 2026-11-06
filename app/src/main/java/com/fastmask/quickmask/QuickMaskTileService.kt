package com.fastmask.quickmask

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.fastmask.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickMaskTileService : TileService() {

    @Inject
    lateinit var runner: QuickMaskRunner

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            runner.createFromTile {
                openAppAndCollapse()
                true
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = getString(R.string.quick_mask_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_mask)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    /**
     * Opens the app from the shade.
     *
     * Both branches use `startActivityAndCollapse`, because collapsing the
     * shade is the point: a plain `startActivity` on API 26–33 leaves
     * MainActivity running UNDER the still-expanded Quick Settings panel, which
     * the user reads as "the tile did nothing" — and this is the path taken
     * whenever the tile refuses to create (signed out, demo, app lock armed).
     *
     * The `UnsupportedOperationException` that made the Intent overload look
     * unusable is gated on `START_ACTIVITY_NEEDS_PENDING_INTENT`, a compat
     * change `@EnabledSince(targetSdkVersion = UPSIDE_DOWN_CAKE)` that the
     * platform only evaluates on API 34+. Below 34 the deprecated overload is
     * both safe and the only one that exists; on 34+ we hand the platform an
     * immutable PendingIntent instead.
     *
     * Lint's StartActivityAndCollapseDeprecated is version-blind — it flags the
     * call even inside an `SDK_INT < 34` branch, where the PendingIntent
     * overload does not exist to migrate to (it would be a NoSuchMethodError).
     * Suppressed here only, and only for that branch.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppAndCollapse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(createAppPendingIntent(this, QuickMaskSlot.CREATED))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(createAppLaunchIntent(this))
        }
    }
}
