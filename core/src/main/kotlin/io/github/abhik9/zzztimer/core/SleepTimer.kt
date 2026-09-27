package io.github.abhik9.zzztimer.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Outcome of an operation that (re)starts the timer. */
sealed interface StartResult {
    data class Started(val timer: Timer) : StartResult

    /** The timer has been stopped (a non positive duration was requested). */
    data object Stopped : StartResult

    /** Nothing changed: the timer can't run until [requirement] is resolved. */
    data class Blocked(val requirement: Requirement) : StartResult
}

/** What to do when the trigger fires or the display is removed, see [SleepTimer.onDeadline]. */
enum class DeadlineOutcome {
    /** The timer ended: fade out and pause playback. */
    SLEEP,

    /** The display was dismissed by the user before the deadline: nothing to do. */
    DISMISSED,
}

/**
 * All the timer operations, shared by the notification actions, the Quick Settings tile, automation intents and the
 * app screen.
 *
 * Stateless and cheap to create: the state lives in the [display] and the [trigger]. Operations are expected to run
 * on a single thread (the Android main thread), which serializes them.
 */
class SleepTimer(
    private val display: TimerDisplay,
    private val trigger: SleepTrigger,
    private val clock: DeviceClock,
    private val settings: () -> TimerSettings,
    /** Called after every change of the timer, e.g. to refresh the Quick Settings tile. */
    private val onChange: () -> Unit = {},
) {

    companion object {
        /**
         * Slack allowed between the deadline and the moment it is signaled. An earlier signal means the display has
         * been dismissed by the user.
         */
        val DEADLINE_TOLERANCE: Duration = 5.seconds
    }

    fun current(): Timer? = display.current()

    fun remaining(): Duration? = current()?.remaining(clock)

    fun missingRequirement(): Requirement? = when {
        !display.isAvailable() -> Requirement.NOTIFICATIONS
        !trigger.isPermitted() -> Requirement.EXACT_ALARMS
        else -> null
    }

    /**
     * Starts a timer of [duration] (capped to [MAX_TIMER_DURATION]), replacing the running one.
     * A non positive [duration] stops the timer.
     */
    fun start(duration: Duration = settings().initial): StartResult {
        if (!duration.isPositive()) {
            stop()
            return StartResult.Stopped
        }
        missingRequirement()?.let { return StartResult.Blocked(it) }
        val timeout = duration.coerceAtMost(MAX_TIMER_DURATION)
        val millis = timeout.inWholeMilliseconds
        val timer = Timer(deadline = clock.elapsedMillis() + millis, endsAt = clock.wallMillis() + millis)
        // Never display a timer that would not pause playback when it ends.
        if (!trigger.arm(timer)) return StartResult.Blocked(Requirement.EXACT_ALARMS)
        display.show(timer, timeout)
        onChange()
        return StartResult.Started(timer)
    }

    fun stop() {
        // Removing the display does not signal the deadline: the trigger must be disarmed explicitly.
        display.hide()
        trigger.disarm()
        onChange()
    }

    fun toggle(): StartResult = if (current() == null) {
        start()
    } else {
        stop()
        StartResult.Stopped
    }

    /**
     * Adds [delta] to the remaining time of the running timer.
     * @param mayEnd whether [delta] is allowed to end the timer. Otherwise such a [delta] is ignored, and the timer is
     * only re-displayed (to refresh its available actions).
     * @return `null` when there is no running timer, or it is already expiring.
     */
    fun adjust(delta: Duration, mayEnd: Boolean = true): StartResult? {
        val remaining = remaining() ?: return null
        // The deadline has been reached, but not signaled yet: restarting or stopping now would skip the sleep.
        if (!remaining.isPositive()) return null
        val next = (remaining + delta).coerceAtMost(MAX_TIMER_DURATION)
        return start(if (next.isPositive() || mayEnd) next else remaining)
    }

    fun extend(): StartResult? = adjust(settings().increment)

    /**
     * Displayed actions can become stale as time goes by: never let a reduction end the timer, as the timer would
     * then stop without pausing playback.
     */
    fun reduce(): StartResult? = adjust(-settings().decrement, mayEnd = false)

    /** Re-displays the running timer, e.g. after a settings change. */
    fun refresh(): StartResult? = adjust(Duration.ZERO)

    /**
     * Handles the signal sent when the trigger fires, or when the display is removed (it timed out, or has been
     * dismissed by the user).
     * @param deadline the [Timer.deadline] the signal was scheduled for, `0` if unknown.
     */
    fun onDeadline(deadline: Long): DeadlineOutcome {
        val early = deadline > 0 && clock.elapsedMillis() < deadline - DEADLINE_TOLERANCE.inWholeMilliseconds
        if (!early) {
            // The display normally removes itself at the deadline, but the trigger may fire first: never leave a timer
            // displayed once it has ended.
            if (deadline > 0 && current()?.deadline == deadline) display.hide()
            onChange()
            return DeadlineOutcome.SLEEP
        }
        // Dismissed by the user: cancel the timer, unless a new one has been started since (which re-armed the trigger).
        if (current() == null) {
            trigger.disarm()
            onChange()
        }
        return DeadlineOutcome.DISMISSED
    }

    fun execute(command: TimerCommand): StartResult? = when (command) {
        is TimerCommand.Start -> start(command.duration ?: settings().initial)
        is TimerCommand.Adjust -> adjust(command.delta)
        TimerCommand.Extend -> extend()
        TimerCommand.Reduce -> reduce()
        TimerCommand.Toggle -> toggle()
        TimerCommand.Stop -> {
            stop()
            StartResult.Stopped
        }
    }
}

/** Operations available to automation tools. */
sealed interface TimerCommand {
    /** Starts a timer of [duration], or of the default duration when `null`. */
    data class Start(val duration: Duration?) : TimerCommand

    data class Adjust(val delta: Duration) : TimerCommand

    data object Extend : TimerCommand

    data object Reduce : TimerCommand

    data object Toggle : TimerCommand

    data object Stop : TimerCommand
}
