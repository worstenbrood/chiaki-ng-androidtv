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
        val primary = viewModel.controllerDevices.firstOrNull { it.descriptor == viewModel.primaryControllerDescriptor }
        TvCycleRow(
            title = stringResource(R.string.primary_controller),
            value = when {
                viewModel.primaryControllerDescriptor.isEmpty() -> stringResource(R.string.primary_controller_auto)
                primary != null -> "${primary.name} (${primary.id})"
                else -> stringResource(R.string.primary_controller_disconnected, viewModel.primaryControllerName)
            },
            onNext = viewModel::nextPrimaryController,
            focusRequester = fr(6), onFocus = fb(6)
        )
        Text(stringResource(R.string.primary_controller_summary),
            style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
        val navigation = viewModel.controllerDevices.firstOrNull { it.descriptor == viewModel.navigationOnlyControllerDescriptor }
        TvCycleRow(
            title = stringResource(R.string.navigation_only_controller),
            value = when {
                viewModel.navigationOnlyControllerDescriptor.isEmpty() -> stringResource(R.string.navigation_only_controller_none)
                navigation != null -> "${navigation.name} (${navigation.id})"
                else -> stringResource(R.string.primary_controller_disconnected, viewModel.navigationOnlyControllerName)
            },
            onNext = viewModel::nextNavigationOnlyController,
            focusRequester = fr(7), onFocus = fb(7)
        )
        Text(stringResource(R.string.navigation_only_controller_summary),
            style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
    }
}
