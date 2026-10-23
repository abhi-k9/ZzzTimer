package io.github.abhik9.zzztimer.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.ShakeSetting
import io.github.abhik9.zzztimer.core.WarningSetting
import io.github.abhik9.zzztimer.core.WarningVibration
import io.github.abhik9.zzztimer.system.ShakeWatcher
import io.github.abhik9.zzztimer.system.vibrate
import java.text.NumberFormat
import kotlin.math.roundToInt

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
        trailingContent = {
            TextButton(onClick = actions::testVibration, enabled = vibration != WarningVibration.OFF) {
                Text(stringResource(R.string.action_test))
            }
        },
        colors = settingsItemColors(),
    )
    // Keep the action out of the selector's width. Scroll rather than split labels at large font sizes.
    SingleChoiceSegmentedButtonRow(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .width(IntrinsicSize.Max)
            .height(IntrinsicSize.Min),
    ) {
        WarningVibration.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = vibration == option,
                onClick = { actions.setWarningVibration(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = WarningVibration.entries.size),
                label = { Text(stringResource(option.label()), maxLines = 1, softWrap = false) },
            )
        }
    }
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
