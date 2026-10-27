package com.chiaking.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.ui.navigation.rememberRouteViewModel

@Composable
fun SettingsRoute(preferences: Preferences, database: AppDatabase, onBack: () -> Unit,
    onShowLogs: () -> Unit, onManageHosts: () -> Unit,
    onExportSettings: () -> Unit, onImportSettings: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel = rememberRouteViewModel { SettingsViewModel(context, database, preferences) }
    SettingsScreen(viewModel, onBack, onShowLogs, onManageHosts, onExportSettings, onImportSettings)
}
