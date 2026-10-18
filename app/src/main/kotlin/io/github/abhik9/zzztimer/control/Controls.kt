package io.github.abhik9.zzztimer.control

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.ControlState
import io.github.abhik9.zzztimer.core.Requirement
import io.github.abhik9.zzztimer.core.Timer
import io.github.abhik9.zzztimer.system.settingsIntent
import io.github.abhik9.zzztimer.system.startSettings
import io.github.abhik9.zzztimer.system.title
import io.github.abhik9.zzztimer.system.toast
import io.github.abhik9.zzztimer.tile.requestTileUpdate
import io.github.abhik9.zzztimer.widget.SleepWidget
import java.util.Date

/*
 * What the one-tap controls (Quick Settings tile, home screen widget) share, so that they can't drift apart. See
 * io.github.abhik9.zzztimer.core.toggleControl for what a tap does.
 */

/** What a one-tap control says about the timer: "Tap to start", or "Until <time>" in the user's time format. */
fun Context.controlLabel(state: ControlState): String = when (state) {
    ControlState.Idle -> getString(R.string.tile_subtitle)
    is ControlState.Running -> getString(R.string.tile_subtitle_running, DateFormat.getTimeFormat(this).format(Date(state.endsAt)))
}

/**
 * Tells the user why the timer can't start, then opens the settings resolving [requirement].
 * @param open runs the opening, e.g. once the device is unlocked.
 * @param start starts a settings screen from the control.
 */
fun Context.resolveFromControl(requirement: Requirement, open: (() -> Unit) -> Unit = { it() }, start: (Intent) -> Unit) {
    toast(requirement.title)
    open { startSettings(settingsIntent(requirement), start = start) }
}

/** Refreshes the controls showing the timer, after it changed to [timer] (`null` when there is none). */
fun Context.requestControlsUpdate(timer: Timer?) {
    // The system asks the tile to render, when it is visible.
    requestTileUpdate()
    // Widgets render right away, with the new state: the notification may briefly still be reported once cancelled.
    SleepWidget.update(this, ControlState.of(timer))
}
