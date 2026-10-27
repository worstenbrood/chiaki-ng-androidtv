package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.BackupSettingsPanel() {
    SettingsCard(SurfaceFrosted) {
        TvActionRow(
            stringResource(R.string.export_settings),
            stringResource(R.string.export_settings_summary),
            onExportSettings,
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvActionRow(
            stringResource(R.string.import_settings),
            stringResource(R.string.import_settings_summary),
            onImportSettings,
            focusRequester = fr(2), onFocus = fb(2)
        )
    }
}
