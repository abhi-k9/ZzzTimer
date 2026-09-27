package io.github.abhik9.zzztimer.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.util.Log
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.Requirement
import io.github.abhik9.zzztimer.core.StartResult
import io.github.abhik9.zzztimer.core.Timer
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.system.message
import io.github.abhik9.zzztimer.system.settingsIntent
import io.github.abhik9.zzztimer.system.startSettings
import io.github.abhik9.zzztimer.system.toast
import java.util.Date

private const val TAG = "SleepTileService"

/** Asks the system to refresh the tile. */
fun Context.requestTileUpdate() {
    try {
        TileService.requestListeningState(this, ComponentName(this, SleepTileService::class.java))
    } catch (e: RuntimeException) {
        // Refused on some devices while the app is in the background: the tile refreshes when it becomes visible anyway.
        Log.w(TAG, "Tile update refused", e)
    }
}

/**
 * Quick Settings tile: tap to start the default timer, or to stop the running one.
 */
class SleepTileService : TileService() {

    override fun onStartListening() = render(sleepTimer().current())

    override fun onClick() {
        when (val result = sleepTimer().toggle()) {
            is StartResult.Started -> render(result.timer)
            // The notification may briefly still be reported as active once cancelled: don't query it.
            StartResult.Stopped -> render(null)
            is StartResult.Blocked -> resolve(result.requirement)
        }
    }

    private fun render(timer: Timer?) {
        val tile = qsTile ?: return
        tile.state = if (timer == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (SDK_INT >= Q) {
            tile.subtitle = if (timer == null) getString(R.string.tile_subtitle)
            else getString(R.string.tile_subtitle_running, DateFormat.getTimeFormat(this).format(Date(timer.endsAt)))
        }
        tile.updateTile()
    }

    private fun resolve(requirement: Requirement) {
        toast(requirement.message)
        startSettings(settingsIntent(requirement).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ::startActivityAndCollapseCompat)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        // Settings can't be displayed on top of the keyguard: the device must be unlocked first.
        if (isLocked) return unlockAndRun { startActivityAndCollapseCompat(intent) }
        if (SDK_INT >= UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
