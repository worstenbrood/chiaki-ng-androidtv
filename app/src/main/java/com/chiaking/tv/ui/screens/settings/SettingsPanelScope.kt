package com.chiaking.tv.ui.screens.settings

import androidx.compose.ui.focus.FocusRequester

/** UI-only callbacks and focus handles shared by the category panels. */
internal class SettingsPanelScope(
    val viewModel: SettingsViewModel,
    val fr: (Int) -> FocusRequester?,
    val fb: (Int) -> (Boolean) -> Unit,
    val onShowLogs: () -> Unit,
    val onManageHosts: () -> Unit,
    val onExportSettings: () -> Unit,
    val onImportSettings: () -> Unit,
    val onEditPin: () -> Unit,
    val onAddPsn: () -> Unit,
    val onAddCoop: () -> Unit,
    val onRemovePsn: (String) -> Unit,
)
