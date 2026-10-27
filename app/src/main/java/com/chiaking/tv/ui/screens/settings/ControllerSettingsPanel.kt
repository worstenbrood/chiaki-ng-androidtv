package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.ControllerSettingsPanel() {
    SettingsCard(SurfaceFrosted) {
        TvSwitchRow(
            stringResource(R.string.swap_cross_moon),
            stringResource(R.string.swap_cross_moon_summary),
            viewModel.swapCrossMoon,
            { viewModel.swapCrossMoon = it },
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvSwitchRow(
            stringResource(R.string.dpad_as_controller),
            stringResource(R.string.dpad_as_controller_summary),
            viewModel.dpadAsController,
            { viewModel.dpadAsController = it },
            focusRequester = fr(2), onFocus = fb(2)
        )
        TvSwitchRow(
            stringResource(R.string.rumble),
            stringResource(R.string.rumble_summary),
            viewModel.rumble,
            { viewModel.rumble = it },
            focusRequester = fr(3), onFocus = fb(3)
        )
        TvSwitchRow(
            stringResource(R.string.motion_controls),
            stringResource(R.string.motion_controls_summary),
            viewModel.motion,
            { viewModel.motion = it },
            focusRequester = fr(4), onFocus = fb(4)
        )
        TvSwitchRow(
            stringResource(R.string.button_haptics),
            stringResource(R.string.button_haptics_summary),
            viewModel.haptics,
            { viewModel.haptics = it },
            focusRequester = fr(5), onFocus = fb(5)
        )
    }
}
