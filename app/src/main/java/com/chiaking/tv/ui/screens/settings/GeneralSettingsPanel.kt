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
internal fun SettingsPanelScope.GeneralSettingsPanel() {
    SettingsCard(SurfaceFrosted) {
        TvSwitchRow(
            stringResource(R.string.console_discovery),
            stringResource(R.string.console_discovery_summary),
            viewModel.discovery,
            { viewModel.discovery = it },
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvSwitchRow(
            stringResource(R.string.auto_connect_startup),
            stringResource(R.string.auto_connect_startup_summary),
            viewModel.autoConnect,
            { viewModel.autoConnect = it },
            focusRequester = fr(2), onFocus = fb(2)
        )
        TvCycleRow(
            title = stringResource(R.string.auto_connect_console),
            value = viewModel.autoConnectOptions.getOrNull(viewModel.autoConnectIndex)?.let {
                it.serverNickname?.takeIf { n -> n.isNotBlank() }
                    ?: stringResource(if(it.target.isPS5) R.string.playstation5 else R.string.playstation4)
            } ?: stringResource(R.string.none),
            onNext = {
                viewModel.nextAutoConnectConsole()
            },
            focusRequester = fr(3), onFocus = fb(3)
        )
        TvCycleRow(
            title = stringResource(R.string.on_close),
            value = stringResource(viewModel.disconnectAction.titleRes),
            onNext = {
                val values = Preferences.DisconnectAction.values()
                viewModel.disconnectAction = values[(viewModel.disconnectAction.ordinal + 1) % values.size]
            },
            focusRequester = fr(4), onFocus = fb(4)
        )
        TvCycleRow(
            title = stringResource(R.string.theme),
            value = stringResource(viewModel.uiTheme.titleRes),
            onNext = {
                val values = Preferences.AppTheme.values()
                viewModel.uiTheme = values[(viewModel.uiTheme.ordinal + 1) % values.size]
            },
            focusRequester = fr(5), onFocus = fb(5)
        )
        TvActionRow(
            stringResource(R.string.registered_consoles),
            if(viewModel.registeredHosts.size == 0) stringResource(R.string.registered_consoles_none)
                else stringResource(R.string.registered_consoles_count, viewModel.registeredHosts.size),
            onManageHosts,
            focusRequester = fr(6), onFocus = fb(6)
        )
        TvActionRow(
            stringResource(R.string.login_pin),
            if(viewModel.loginPin.isEmpty()) stringResource(R.string.login_pin_not_set)
                else stringResource(R.string.login_pin_summary_default),
            { onEditPin() },
            focusRequester = fr(7), onFocus = fb(7)
        )
        TvSwitchRow(
            stringResource(R.string.verbose_logs),
            stringResource(R.string.verbose_logs_summary),
            viewModel.logVerbose,
            { viewModel.logVerbose = it },
            focusRequester = fr(8), onFocus = fb(8)
        )
        TvActionRow(
            stringResource(R.string.session_logs),
            stringResource(R.string.session_logs_summary),
            onShowLogs,
            focusRequester = fr(9), onFocus = fb(9)
        )
    }
}
