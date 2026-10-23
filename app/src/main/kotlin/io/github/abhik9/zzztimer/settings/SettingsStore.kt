package io.github.abhik9.zzztimer.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import io.github.abhik9.zzztimer.core.DurationSetting
import io.github.abhik9.zzztimer.core.FadeSetting
import io.github.abhik9.zzztimer.core.TimerSettings
import io.github.abhik9.zzztimer.core.WarningSetting
import io.github.abhik9.zzztimer.core.WarningVibration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.time.Duration.Companion.minutes

/** Night mode of the app, independent of the system one. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Snapshot of all the user settings. */
data class UserSettings(
    val durations: Map<DurationSetting, Int> = DurationSetting.entries.associateWith { it.defaultMinutes },
    val fadeSeconds: Int = FadeSetting.DEFAULT_SECONDS,
    val warningVibration: WarningVibration = WarningVibration.DEFAULT,
    val warningMinutes: Int = WarningSetting.DEFAULT_MINUTES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val automationEnabled: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
) {
    fun minutes(setting: DurationSetting): Int = durations[setting] ?: setting.defaultMinutes
}

/**
 * User settings, stored in [SharedPreferences]: they are read synchronously by broadcast receivers and the tile.
 * Every read is validated, so that a corrupted or tampered file can only fall back to the defaults.
 */
class SettingsStore private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "SettingsStore"
        private const val FILE = "settings"
        private const val KEY_FADE = "fade_seconds"
        private const val KEY_WARNING_VIBRATION = "warning_vibration"
        private const val KEY_WARNING_MINUTES = "warning_minutes"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AUTOMATION = "automation"
        private const val KEY_DIAGNOSTICS = "diagnostics"
        private const val KEY_TEST_DEADLINE = "test_deadline"

        private val DurationSetting.key: String
            get() = when (this) {
                DurationSetting.INITIAL -> "timeout_initial_minutes"
                DurationSetting.INCREMENT -> "timeout_increment_minutes"
                DurationSetting.DECREMENT -> "timeout_decrement_minutes"
            }

        fun from(context: Context) = SettingsStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }

    fun minutes(setting: DurationSetting): Int = setting.sanitize(read(setting.key, setting.defaultMinutes, prefs::getInt))

    fun setMinutes(setting: DurationSetting, minutes: Int) = prefs.edit { putInt(setting.key, setting.sanitize(minutes)) }

    fun timerSettings(): TimerSettings =
        TimerSettings.ofMinutes(::minutes).copy(warningLead = if (warns) warningMinutes.minutes else null)

    /** Whether the user is warned that a timer ends soon, see [TimerSettings.warningLead]. */
    private val warns: Boolean get() = warningVibration != WarningVibration.OFF

    var warningVibration: WarningVibration
        get() = readEnum(KEY_WARNING_VIBRATION, WarningVibration.DEFAULT)
        set(value) = prefs.edit { putString(KEY_WARNING_VIBRATION, value.name) }

    var warningMinutes: Int
        get() = WarningSetting.sanitize(read(KEY_WARNING_MINUTES, WarningSetting.DEFAULT_MINUTES, prefs::getInt))
        set(value) = prefs.edit { putInt(KEY_WARNING_MINUTES, WarningSetting.sanitize(value)) }

    /** How long the volume fades out when a timer ends, see [FadeSetting]. */
    var fadeSeconds: Int
        get() = FadeSetting.sanitize(read(KEY_FADE, FadeSetting.DEFAULT_SECONDS, prefs::getInt))
        set(value) = prefs.edit { putInt(KEY_FADE, FadeSetting.sanitize(value)) }

    var themeMode: ThemeMode
        get() = readEnum(KEY_THEME, ThemeMode.SYSTEM)
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    var dynamicColor: Boolean
        get() = read(KEY_DYNAMIC_COLOR, true, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }

    /** Whether other apps may control the timer, see [io.github.abhik9.zzztimer.automation.AutomationReceiver]. */
    var automationEnabled: Boolean
        get() = read(KEY_AUTOMATION, true, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_AUTOMATION, value) }

    /** Whether the [io.github.abhik9.zzztimer.diagnostics.DiagnosticsLog] is recording. */
    var diagnosticsEnabled: Boolean
        get() = read(KEY_DIAGNOSTICS, false, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_DIAGNOSTICS, value) }

    /** Deadline of the running test of the timer end, `0` when none, see [io.github.abhik9.zzztimer.core.TimerEndTest]. */
    var testDeadline: Long
        get() = read(KEY_TEST_DEADLINE, 0L, prefs::getLong)
        set(value) = prefs.edit { putLong(KEY_TEST_DEADLINE, value) }

    fun snapshot() = UserSettings(
        durations = DurationSetting.entries.associateWith(::minutes),
        fadeSeconds = fadeSeconds,
        warningVibration = warningVibration,
        warningMinutes = warningMinutes,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        automationEnabled = automationEnabled,
        diagnosticsEnabled = diagnosticsEnabled,
    )

    /** Emits the current settings, then every change. */
    val changes: Flow<UserSettings> = callbackFlow {
        // The listener is referenced until the flow completes: SharedPreferences only keeps weak references.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(snapshot())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /** An enum stored by name: an unknown one falls back to the default. */
    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E =
        read(key, null, prefs::getString)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    /** A value stored with another type throws [ClassCastException]: fall back to the default. */
    private inline fun <T> read(key: String, default: T, getter: (String, T) -> T): T = try {
        getter(key, default)
    } catch (e: ClassCastException) {
        Log.w(TAG, "Invalid value for $key", e)
        default
    }
}
