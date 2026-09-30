package io.github.abhik9.zzztimer.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.DurationSetting
import io.github.abhik9.zzztimer.core.Requirement
import io.github.abhik9.zzztimer.core.Timer
import io.github.abhik9.zzztimer.settings.ThemeMode
import io.github.abhik9.zzztimer.system.explanation
import io.github.abhik9.zzztimer.system.title
import io.github.abhik9.zzztimer.ui.theme.ZzzTimerTheme

/** User intents of the [MainScreen], implemented by [MainViewModel]. */
interface MainActions {
    fun start(minutes: Int)
    fun stop()
    fun extend()
    fun reduce()
    fun setMinutes(setting: DurationSetting, minutes: Int)
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColor(enabled: Boolean)
    fun setAutomationEnabled(enabled: Boolean)
    fun setDiagnosticsEnabled(enabled: Boolean)
    fun clearDiagnostics()
}

/**
 * @param onResolve asks the user to resolve a [Requirement] (permission prompt or system settings).
 * @param onAllowMediaAccess opens the Notification access settings, which let ZzzTimer rewind the playing media.
 * @param onExportDiagnostics asks the user where to export the diagnostics.
 * @param elapsedNow the `elapsedRealtime` clock, the timeline of [Timer.deadline].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    actions: MainActions,
    onResolve: (Requirement) -> Unit,
    onAllowMediaAccess: () -> Unit,
    onExportDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    elapsedNow: () -> Long = SystemClock::elapsedRealtime,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf<DurationSetting?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // One warning at a time, in the order they must be resolved.
                state.missingRequirement?.let { requirement ->
                    WarningCard(title = requirement.title, body = requirement.explanation, onClick = { onResolve(requirement) })
                }
                TimerCard(state, actions, elapsedNow)
                StartTimerCard(state, actions, elapsedNow)
                SectionHeader(R.string.section_durations)
                DurationsCard(state, onEdit = { editing = it })
                SectionHeader(R.string.section_timer_end)
                TimerEndCard(state, onAllowMediaAccess)
                SectionHeader(R.string.section_appearance)
                AppearanceCard(state, actions)
                SectionHeader(R.string.section_automation)
                AutomationCard(state, actions)
                SectionHeader(R.string.section_diagnostics)
                DiagnosticsCard(state, actions, onExportDiagnostics)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    editing?.let { setting ->
        MinutesDialog(
            setting = setting,
            initial = state.settings.minutes(setting),
            onDismiss = { editing = null },
            onConfirm = {
                actions.setMinutes(setting, it)
                editing = null
            },
        )
    }
}

//region Previews
private object PreviewActions : MainActions {
    override fun start(minutes: Int) = Unit
    override fun stop() = Unit
    override fun extend() = Unit
    override fun reduce() = Unit
    override fun setMinutes(setting: DurationSetting, minutes: Int) = Unit
    override fun setThemeMode(mode: ThemeMode) = Unit
    override fun setDynamicColor(enabled: Boolean) = Unit
    override fun setAutomationEnabled(enabled: Boolean) = Unit
    override fun setDiagnosticsEnabled(enabled: Boolean) = Unit
    override fun clearDiagnostics() = Unit
}

@Composable
private fun PreviewScreen(state: MainUiState) =
    MainScreen(state, PreviewActions, onResolve = {}, onAllowMediaAccess = {}, onExportDiagnostics = {}, elapsedNow = { 0L })

@Preview(name = "Idle")
@Composable
private fun IdlePreview() = ZzzTimerTheme(ThemeMode.LIGHT, dynamicColor = false) {
    PreviewScreen(MainUiState(missingRequirement = Requirement.NOTIFICATIONS))
}

@Preview(name = "Running (dark)")
@Composable
private fun RunningPreview() = ZzzTimerTheme(ThemeMode.DARK, dynamicColor = false) {
    PreviewScreen(MainUiState(timer = Timer(deadline = 23 * 60_000L + 41_000L, endsAt = 0L)))
}
//endregion
