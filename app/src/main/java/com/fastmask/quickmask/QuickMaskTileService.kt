package com.fastmask.quickmask

import android.app.PendingIntent
import android.content.Intent
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

    private fun openAppAndCollapse() {
        val launchIntent = createAppLaunchIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                QUICK_MASK_OPEN_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(Intent(launchIntent))
        }
    }
}
