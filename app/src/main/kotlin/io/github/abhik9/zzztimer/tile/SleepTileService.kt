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
import io.github.abhik9.zzztimer.control.controlLabel
import io.github.abhik9.zzztimer.control.resolveFromControl
import io.github.abhik9.zzztimer.core.ControlOutcome
import io.github.abhik9.zzztimer.core.ControlState
import io.github.abhik9.zzztimer.core.toggleControl
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.sleepTimer

/** Asks the system to refresh the tile. */
fun Context.requestTileUpdate() {
    try {
        TileService.requestListeningState(this, ComponentName(this, SleepTileService::class.java))
    } catch (e: RuntimeException) {
        // Refused on some devices while the app is in the background: the tile refreshes when it becomes visible anyway.
        diagnostics.warn("tile: update refused", e)
    }
}

/**
 * Quick Settings tile: tap to start the default timer, or to stop the running one.
 */
class SleepTileService : TileService() {

    override fun onStartListening() = render(ControlState.of(sleepTimer().current()))

    override fun onClick() {
        diagnostics.record { "tile: click, locked=$isLocked" }
        when (val outcome = sleepTimer().toggleControl()) {
            is ControlOutcome.Show -> render(outcome.state)
            is ControlOutcome.Resolve -> resolveFromControl(
                outcome.requirement,
                // Settings can't be displayed on top of the keyguard: the device must be unlocked first.
                open = { if (isLocked) unlockAndRun(it) else it() },
                start = ::startActivityAndCollapseCompat,
            )
        }
    }

    private fun render(state: ControlState) {
        val tile = qsTile ?: return
        tile.state = if (state is ControlState.Running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (SDK_INT >= Q) tile.subtitle = controlLabel(state)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        // Started from a service: there is no task to start it in.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (SDK_INT >= UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
