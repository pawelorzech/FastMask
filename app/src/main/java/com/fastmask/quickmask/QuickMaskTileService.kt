package com.fastmask.quickmask

import android.app.PendingIntent
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
            runner.launchCreate(openApp = ::openAppAndCollapse)
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
     * `startActivityAndCollapse(Intent)` is not an option on any device: with
     * targetSdk 34+ the platform throws UnsupportedOperationException from it,
     * so the deprecated overload would crash the tile rather than open the app.
     * On 34+ we hand the platform an immutable PendingIntent; below that the
     * PendingIntent overload does not exist yet, so we start the activity
     * directly (SystemUI binds this service, which keeps the background
     * activity launch allowed) and let the shade close behind it.
     */
    private fun openAppAndCollapse() {
        val launchIntent = createAppLaunchIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                QUICK_MASK_OPEN_REQUEST_CODE,
                launchIntent,
                // Immutable: the system must not be able to rewrite where this goes.
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivity(launchIntent)
        }
    }
}
