package io.github.abhik9.zzztimer.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.CINNAMON_BUN
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.abhik9.zzztimer.R

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
