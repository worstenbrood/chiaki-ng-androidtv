package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.AudioSettingsPanel() {
    SettingsCard(SurfaceFrosted) {
        TvStepperRow(
            title = stringResource(R.string.volume),
            value = stringResource(R.string.volume_percent, viewModel.volume),
            summary = stringResource(R.string.volume_step_hint),
            onStep = { delta ->
                viewModel.volume = (viewModel.volume + delta * 5).coerceIn(0, 100)
            },
            onReset = {
                viewModel.volume = 100
            },
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvCycleRow(
            title = stringResource(R.string.output_device),
            value = viewModel.audioDevices[viewModel.deviceIndex].first,
            onNext = {
                viewModel.nextAudioDevice()
            },
            focusRequester = fr(2), onFocus = fb(2)
        )
        TvStepperRow(
            title = stringResource(R.string.audio_buffer),
            value = if(viewModel.bufferMs == 0) stringResource(R.string.auto) else stringResource(R.string.buffer_ms, viewModel.bufferMs),
            summary = stringResource(R.string.buffer_step_hint),
            onStep = { delta ->
                viewModel.bufferMs = (viewModel.bufferMs + delta * 10).coerceIn(0, 100)
            },
            onReset = {
                viewModel.bufferMs = 0
            },
            focusRequester = fr(3), onFocus = fb(3)
        )
    }
}
