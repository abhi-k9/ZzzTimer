package io.github.abhik9.zzztimer.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.CINNAMON_BUN
import android.os.SystemClock
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.DurationSetting
import io.github.abhik9.zzztimer.core.FadeSetting
import io.github.abhik9.zzztimer.core.ShakeSetting
import io.github.abhik9.zzztimer.core.WarningSetting
import io.github.abhik9.zzztimer.core.WarningVibration
import io.github.abhik9.zzztimer.settings.ThemeMode
import io.github.abhik9.zzztimer.system.ShakeWatcher
import io.github.abhik9.zzztimer.system.vibrate
import java.text.NumberFormat
import java.util.Calendar
import kotlin.math.roundToInt

@get:StringRes
private val DurationSetting.title: Int
    get() = when (this) {
        DurationSetting.INITIAL -> R.string.settings_initial
        DurationSetting.INCREMENT -> R.string.settings_increment
        DurationSetting.DECREMENT -> R.string.settings_decrement
    }

@get:StringRes
private val DurationSetting.description: Int
    get() = when (this) {
        DurationSetting.INITIAL -> R.string.settings_initial_description
        DurationSetting.INCREMENT -> R.string.settings_increment_description
        DurationSetting.DECREMENT -> R.string.settings_decrement_description
    }

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

@Composable
internal fun DurationsCard(state: MainUiState, onEdit: (DurationSetting) -> Unit, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    DurationSetting.entries.forEachIndexed { index, setting ->
        if (index > 0) SettingsDivider()
        val minutes = state.settings.minutes(setting)
        ListItem(
            headlineContent = { Text(stringResource(setting.title)) },
            supportingContent = { Text(stringResource(setting.description)) },
            trailingContent = {
                val value = when (setting) {
                    DurationSetting.INITIAL -> formatMinutes(minutes)
                    DurationSetting.INCREMENT -> stringResource(R.string.action_extend, minutes)
                    DurationSetting.DECREMENT -> stringResource(R.string.action_reduce, minutes)
                }
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            },
            colors = settingsItemColors(),
            modifier = Modifier.clickable { onEdit(setting) },
        )
    }
}

@Composable
internal fun AppearanceCard(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.theme_title)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.settings.themeMode == mode,
                        onClick = { actions.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        label = { Text(stringResource(mode.label)) },
                    )
                }
            }
        },
        colors = settingsItemColors(),
    )
    if (state.dynamicColorAvailable) {
        SettingsDivider()
        SwitchItem(
            title = R.string.dynamic_color_title,
            description = R.string.dynamic_color_description,
            checked = state.settings.dynamicColor,
            onCheckedChange = actions::setDynamicColor,
        )
    }
}

@Composable
internal fun TimerEndCard(
    state: MainUiState,
    actions: MainActions,
    onAllowMediaAccess: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsCard(modifier) {
    SliderItem(
        title = R.string.fade_title,
        description = R.string.fade_description,
        value = state.settings.fadeSeconds,
        range = FadeSetting.range,
        step = FadeSetting.STEP_SECONDS,
        label = { if (it == 0) stringResource(R.string.fade_off) else formatSeconds(it) },
        onChange = actions::setFadeSeconds,
    )
    SettingsDivider()
    // A system permission rather than a setting: both ways lead to its settings screen.
    SwitchItem(
        title = R.string.rewind_title,
        description = R.string.rewind_description,
        checked = state.mediaAccess,
        onCheckedChange = { onAllowMediaAccess() },
    )
    SettingsDivider()
    // A real timer: the sleep runs in the background, as at night, and reports what it could do.
    val idle = state.timer == null
    ListItem(
        headlineContent = { Text(stringResource(R.string.test_title)) },
        supportingContent = { Text(stringResource(if (idle) R.string.test_description else R.string.test_unavailable)) },
        colors = settingsItemColors(),
        modifier = Modifier.clickable(enabled = idle, onClick = actions::testTimerEnd),
    )
}

/** Labels of the [ShakeSetting] levels. */
private val SHAKE_LEVELS = listOf(
    R.string.shake_level_1,
    R.string.shake_level_2,
    R.string.shake_level_3,
    R.string.shake_level_4,
    R.string.shake_level_5,
)

/** Shows the live acceleration and the shakes detected at [level], which can be changed meanwhile. */
@Composable
private fun ShakeTestDialog(level: Int, onLevelChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var force by remember { mutableDoubleStateOf(1.0) }
    var detected by remember { mutableIntStateOf(0) }
    var available by remember { mutableStateOf(true) }
    DisposableEffect(level) {
        var peakAt = 0L
        val watcher = ShakeWatcher(
            context,
            level,
            onForce = { g ->
                // Holds peaks for a moment, so that they can be read.
                val now = SystemClock.elapsedRealtime()
                if (g >= force || now - peakAt > 600) {
                    force = g
                    peakAt = now
                }
            },
            onShake = {
                detected++
                context.vibrate(WarningVibration.LIGHT)
            },
        )
        available = watcher.start()
        onDispose { watcher.stop() }
    }
    val numbers = remember {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }
    val threshold = ShakeSetting.thresholdG(level)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shake_test_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (available) R.string.shake_test_description else R.string.shake_unavailable))
                LinearProgressIndicator(
                    progress = { (force / SHAKE_TEST_MAX_G).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.shake_test_force, numbers.format(force), numbers.format(threshold)))
                if (detected > 0) {
                    Text(
                        stringResource(R.string.shake_test_detected, NumberFormat.getIntegerInstance().format(detected)),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(stringResource(R.string.shake_strength_title), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { onLevelChange(it.roundToInt()) },
                    valueRange = ShakeSetting.range.first.toFloat()..ShakeSetting.range.last.toFloat(),
                    steps = ShakeSetting.range.last - ShakeSetting.range.first - 1,
                )
                Text(stringResource(SHAKE_LEVELS[level - ShakeSetting.range.first]))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

/** Full scale of the acceleration shown while testing, in g. */
private const val SHAKE_TEST_MAX_G = 4.0

@StringRes
private fun WarningVibration.label(): Int = when (this) {
    WarningVibration.OFF -> R.string.vibration_off
    WarningVibration.LIGHT -> R.string.vibration_light
    WarningVibration.MEDIUM -> R.string.vibration_medium
    WarningVibration.STRONG -> R.string.vibration_strong
}

@Composable
internal fun BeforeEndCard(
    state: MainUiState,
    actions: MainActions,
    onAllowExactAlarms: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsCard(modifier) {
    val vibration = state.settings.warningVibration
    ListItem(
        headlineContent = { Text(stringResource(R.string.vibration_title)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                WarningVibration.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = vibration == option,
                        onClick = { actions.setWarningVibration(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = WarningVibration.entries.size),
                        label = { Text(stringResource(option.label())) },
                    )
                }
            }
        },
        trailingContent = {
            TextButton(onClick = actions::testVibration, enabled = vibration != WarningVibration.OFF) {
                Text(stringResource(R.string.action_test))
            }
        },
        colors = settingsItemColors(),
    )
    SettingsDivider()
    SliderItem(
        title = R.string.warning_title,
        description = R.string.warning_description,
        value = state.settings.warningMinutes,
        range = WarningSetting.range,
        step = 1,
        label = { formatMinutes(it) },
        onChange = actions::setWarningMinutes,
    )
    SettingsDivider()
    SwitchItem(
        title = R.string.shake_title,
        description = R.string.shake_description,
        checked = state.settings.shakeEnabled,
        onCheckedChange = actions::setShakeEnabled,
    )
    if (state.settings.shakeEnabled) {
        var testing by rememberSaveable { mutableStateOf(false) }
        SettingsDivider()
        SliderItem(
            title = R.string.shake_strength_title,
            description = R.string.shake_strength_description,
            value = state.settings.shakeLevel,
            range = ShakeSetting.range,
            step = 1,
            label = { stringResource(SHAKE_LEVELS[it - ShakeSetting.range.first]) },
            onChange = actions::setShakeLevel,
        )
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.shake_test_title)) },
            supportingContent = { Text(stringResource(R.string.shake_test_item_description)) },
            colors = settingsItemColors(),
            modifier = Modifier.clickable { testing = true },
        )
        if (testing) {
            ShakeTestDialog(level = state.settings.shakeLevel, onLevelChange = actions::setShakeLevel, onDismiss = { testing = false })
        }
    }
    // Before Android 17, the warning works without the permission, but may come late.
    if (state.warns && !state.warningOnTime) {
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.warning_late_title)) },
            supportingContent = { Text(stringResource(R.string.warning_late_description)) },
            colors = settingsItemColors(),
            modifier = Modifier.clickable(onClick = onAllowExactAlarms),
        )
    }
}

@Composable
internal fun BedtimeCard(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    SwitchItem(
        title = R.string.bedtime_title,
        description = R.string.bedtime_description,
        checked = state.settings.bedtimeEnabled,
        onCheckedChange = actions::setBedtimeEnabled,
    )
    if (state.settings.bedtimeEnabled) {
        var picking by rememberSaveable { mutableStateOf(false) }
        val minute = state.settings.bedtimeMinute
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.bedtime_time_title)) },
            trailingContent = {
                Text(formatTimeOfDay(minute), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            },
            colors = settingsItemColors(),
            modifier = Modifier.clickable { picking = true },
        )
        if (SDK_INT >= CINNAMON_BUN) {
            SettingsDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.bedtime_android_17), style = MaterialTheme.typography.bodyMedium) },
                colors = settingsItemColors(),
            )
        }
        if (picking) {
            BedtimeDialog(
                minute = minute,
                onDismiss = { picking = false },
                onConfirm = {
                    actions.setBedtimeMinute(it)
                    picking = false
                },
            )
        }
    }
}

/** A time of the day, in [minute]s, in the user's time format. */
@Composable
private fun formatTimeOfDay(minute: Int): String {
    val context = LocalContext.current
    return remember(minute, context) {
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }.time
        DateFormat.getTimeFormat(context).format(time)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeDialog(minute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val picker = rememberTimePickerState(
        initialHour = minute / 60,
        initialMinute = minute % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bedtime_time_title)) },
        text = { TimeInput(state = picker) },
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun AutomationCard(state: MainUiState, actions: MainActions, modifier: Modifier = Modifier) = SettingsCard(modifier) {
    SwitchItem(
        title = R.string.automation_title,
        description = R.string.automation_description,
        checked = state.settings.automationEnabled,
        onCheckedChange = actions::setAutomationEnabled,
    )
}

@Composable
internal fun DiagnosticsCard(
    state: MainUiState,
    actions: MainActions,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsCard(modifier) {
    SwitchItem(
        title = R.string.diagnostics_title,
        description = R.string.diagnostics_description,
        checked = state.settings.diagnosticsEnabled,
        onCheckedChange = actions::setDiagnosticsEnabled,
    )
    // Only once something has been recorded.
    if (state.diagnosticsLogBytes > 0) {
        val size = Formatter.formatShortFileSize(LocalContext.current, state.diagnosticsLogBytes)
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.diagnostics_export)) },
            supportingContent = { Text(stringResource(R.string.diagnostics_export_description, size)) },
            colors = settingsItemColors(),
            modifier = Modifier.clickable(onClick = onExport),
        )
        SettingsDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.diagnostics_clear)) },
            colors = settingsItemColors(),
            modifier = Modifier.clickable(onClick = actions::clearDiagnostics),
        )
    }
}

/** Picks a value of [range] in [step]s, saved once released. */
@Composable
private fun SliderItem(
    @StringRes title: Int,
    @StringRes description: Int,
    value: Int,
    range: IntRange,
    step: Int,
    label: @Composable (Int) -> String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follows the slider while it is dragged.
    var current by remember(value) { mutableIntStateOf(value) }
    val currentLabel = label(current)
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Column {
                Text(stringResource(description))
                Slider(
                    value = current.toFloat(),
                    onValueChange = { current = it.roundToInt() },
                    onValueChangeFinished = { onChange(current) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first) / step - 1,
                    modifier = Modifier.semantics { stateDescription = currentLabel },
                )
            }
        },
        trailingContent = { Text(currentLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        colors = settingsItemColors(),
        modifier = modifier,
    )
}

@Composable
private fun SwitchItem(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = ListItem(
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(description)) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    colors = settingsItemColors(),
    modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
)

@Composable
internal fun MinutesDialog(setting: DurationSetting, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in setting.range }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(setting.title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                singleLine = true,
                suffix = { Text(stringResource(R.string.unit_minutes)) },
                isError = value == null,
                supportingText = {
                    Text(stringResource(R.string.settings_range, formatMinutes(setting.range.first), formatMinutes(setting.range.last)))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onConfirm) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
