package com.chiaking.tv.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.session.MicCapture
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.MicSettingsPanel() {
    val ctx = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.micEnabled = it }
    SettingsCard(SurfaceFrosted) {
        TvSwitchRow(
            stringResource(R.string.mic_enabled),
            stringResource(R.string.mic_enabled_summary),
            viewModel.micEnabled,
            { on ->
                if(on)
                {
                    if(MicCapture.hasPermission(ctx))
                    {
                        viewModel.micEnabled = true
                    }
                    else
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                else
                {
                    viewModel.micEnabled = false
                }
            },
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvSwitchRow(
            stringResource(R.string.mic_start_unmuted),
            stringResource(R.string.mic_start_unmuted_summary),
            viewModel.micStartUnmuted,
            { viewModel.micStartUnmuted = it },
            focusRequester = fr(2), onFocus = fb(2)
        )
        TvCycleRow(
            title = stringResource(R.string.mic_device),
            value = viewModel.micDevices[viewModel.micDeviceIndex].first,
            onNext = {
                viewModel.nextMicDevice()
            },
            focusRequester = fr(3), onFocus = fb(3)
        )
        TvStepperRow(
            title = stringResource(R.string.mic_gain),
            value = stringResource(R.string.mic_gain_percent, viewModel.micGain),
            summary = stringResource(R.string.mic_gain_step_hint),
            onStep = { delta ->
                viewModel.micGain = (viewModel.micGain + delta * 5).coerceIn(25, 300)
            },
            onReset = {
                viewModel.micGain = 100
            },
            focusRequester = fr(4), onFocus = fb(4)
        )
        TvSwitchRow(
            stringResource(R.string.mic_aec),
            stringResource(R.string.mic_aec_summary),
            viewModel.micAec,
            { viewModel.micAec = it },
            focusRequester = fr(5), onFocus = fb(5)
        )
        TvSwitchRow(
            stringResource(R.string.mic_ns),
            stringResource(R.string.mic_ns_summary),
            viewModel.micNs,
            { viewModel.micNs = it },
            focusRequester = fr(6), onFocus = fb(6)
        )
        TvSwitchRow(
            stringResource(R.string.mic_agc),
            stringResource(R.string.mic_agc_summary),
            viewModel.micAgc,
            { viewModel.micAgc = it },
            focusRequester = fr(7), onFocus = fb(7)
        )
        TvCycleRow(
            title = stringResource(R.string.mic_complexity),
            value = viewModel.micComplexity.title,
            onNext = {
                val values = Preferences.MicComplexity.values()
                viewModel.micComplexity = values[(viewModel.micComplexity.ordinal + 1) % values.size]
            },
            focusRequester = fr(8), onFocus = fb(8)
        )
        TvCycleRow(
            title = stringResource(R.string.mic_bandwidth),
            value = viewModel.micBandwidth.title,
            onNext = {
                val values = Preferences.MicBandwidth.values()
                viewModel.micBandwidth = values[(viewModel.micBandwidth.ordinal + 1) % values.size]
            },
            focusRequester = fr(9), onFocus = fb(9)
        )
        TvSwitchRow(
            stringResource(R.string.mic_pitch),
            stringResource(R.string.mic_pitch_summary),
            viewModel.micPitchEnabled,
            { viewModel.micPitchEnabled = it },
            focusRequester = fr(10), onFocus = fb(10)
        )
        TvStepperRow(
            title = stringResource(R.string.mic_pitch_value),
            value = stringResource(R.string.mic_pitch_semitones, viewModel.micPitch),
            summary = stringResource(R.string.mic_pitch_step_hint),
            onStep = { delta ->
                viewModel.micPitch = (viewModel.micPitch + delta).coerceIn(-12, 12)
            },
            onReset = {
                viewModel.micPitch = 0
            },
            focusRequester = fr(11), onFocus = fb(11)
        )
    }
}
