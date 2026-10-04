package io.github.abhik9.zzztimer.ui

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.abhik9.zzztimer.R

@Composable
internal fun SectionHeader(@StringRes title: Int, modifier: Modifier = Modifier) = Text(
    text = stringResource(title),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier
        .padding(start = 16.dp, top = 12.dp)
        .semantics { heading() },
)

/** A card holding a list of settings. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    content = content,
)

@Composable
internal fun settingsItemColors(): ListItemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh)

@Composable
internal fun WarningCard(@StringRes title: Int, @StringRes body: Int, onClick: () -> Unit, modifier: Modifier = Modifier) = Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ),
) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.warning_action))
        }
    }
}

/**
 * A duration in whole minutes, localized by ICU, e.g. "1 hr, 30 min".
 */
@Composable
internal fun formatMinutes(minutes: Int): String = rememberDurationFormat().formatParts(minutes, MeasureUnit.HOUR, MeasureUnit.MINUTE)

/**
 * A duration in whole seconds, localized by ICU, e.g. "45 sec" or "1 min".
 */
@Composable
internal fun formatSeconds(seconds: Int): String = rememberDurationFormat().formatParts(seconds, MeasureUnit.MINUTE, MeasureUnit.SECOND)

@Composable
private fun rememberDurationFormat(): MeasureFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT) }
}

/** [value] [small] units, split into [large] units (worth 60 [small] units) and the rest, e.g. "1 hr, 30 min". */
private fun MeasureFormat.formatParts(value: Int, large: MeasureUnit, small: MeasureUnit): String {
    val whole = value / 60
    val rest = value % 60
    return when {
        whole == 0 -> format(Measure(rest, small))
        rest == 0 -> format(Measure(whole, large))
        else -> formatMeasures(Measure(whole, large), Measure(rest, small))
    }
}
