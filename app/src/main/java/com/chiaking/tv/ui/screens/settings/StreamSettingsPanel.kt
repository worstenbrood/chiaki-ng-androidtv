package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.StreamSettingsPanel() {
    SettingsCard(SurfaceFrosted) {
        TvCycleRow(
            title = stringResource(R.string.resolution),
            value = viewModel.resolution.title,
            onNext = {
                viewModel.resolution = Preferences.Resolution.values()[(viewModel.resolution.ordinal + 1) % Preferences.Resolution.values().size]
            },
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvCycleRow(
            title = stringResource(R.string.frames_per_second),
            value = viewModel.fps.title,
            onNext = {
                viewModel.fps = Preferences.FPS.values()[(viewModel.fps.ordinal + 1) % Preferences.FPS.values().size]
            },
            focusRequester = fr(2), onFocus = fb(2)
        )
        TvCycleRow(
            title = stringResource(R.string.video_codec),
            value = viewModel.codec.title,
            onNext = {
                viewModel.codec = Preferences.Codec.values()[(viewModel.codec.ordinal + 1) % Preferences.Codec.values().size]
            },
            focusRequester = fr(3), onFocus = fb(3)
        )
        TvStepperRow(
            title = stringResource(R.string.bitrate),
            value = if(viewModel.bitrate == null) stringResource(R.string.bitrate_auto, viewModel.bitrateAuto)
                else stringResource(R.string.bitrate_kbps, viewModel.bitrate!!),
            summary = stringResource(R.string.bitrate_summary),
            onStep = { steps ->
                val base = viewModel.bitrate ?: viewModel.bitrateAuto
                viewModel.bitrate = (base + steps * Preferences.BITRATE_STEP_KBPS)
                    .coerceIn(Preferences.BITRATE_MIN_KBPS, Preferences.BITRATE_MAX_KBPS)
            },
            onReset = {
                viewModel.bitrate = if(viewModel.bitrate == null) viewModel.bitrateAuto else null
            },
            focusRequester = fr(4), onFocus = fb(4)
        )
        TvCycleRow(
            title = stringResource(R.string.display_mode),
            value = stringResource(viewModel.displayMode.titleRes),
            onNext = {
                val values = Preferences.DisplayMode.values()
                viewModel.displayMode = values[(viewModel.displayMode.ordinal + 1) % values.size]
            },
            focusRequester = fr(5), onFocus = fb(5)
        )
        TvSwitchRow(
            stringResource(R.string.idr_on_fec_failure),
            stringResource(R.string.idr_on_fec_failure_summary),
            viewModel.idrOnFec,
            { viewModel.idrOnFec = it },
            focusRequester = fr(6), onFocus = fb(6)
        )
    }
}
