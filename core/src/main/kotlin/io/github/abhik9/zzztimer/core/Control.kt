package io.github.abhik9.zzztimer.core

/**
 * What the one-tap controls of the timer show: the Quick Settings tile and the home screen widget. A tap starts the
 * default timer, or stops the running one, see [toggleControl].
 */
sealed interface ControlState {
    /** No timer: a tap starts one of the default duration. */
    data object Idle : ControlState

    /** A timer runs until [endsAt], on the wall clock (see [Timer.endsAt]): a tap stops it. */
    data class Running(val endsAt: Long) : ControlState

    companion object {
        fun of(timer: Timer?): ControlState = if (timer == null) Idle else Running(timer.endsAt)
    }
}

/** What a one-tap control does once tapped. */
sealed interface ControlOutcome {
    /** Show [state]. */
    data class Show(val state: ControlState) : ControlOutcome

    /** The timer can't start: tell the user why, and open the settings resolving [requirement]. */
    data class Resolve(val requirement: Requirement) : ControlOutcome
}

/** Handles a tap on a one-tap control: [SleepTimer.toggle], then what the control does next. */
fun SleepTimer.toggleControl(): ControlOutcome = when (val result = toggle()) {
    is StartResult.Started -> ControlOutcome.Show(ControlState.of(result.timer))
    // The display may briefly still report the stopped timer: don't query it.
    StartResult.Stopped -> ControlOutcome.Show(ControlState.Idle)
    is StartResult.Blocked -> ControlOutcome.Resolve(result.requirement)
}
