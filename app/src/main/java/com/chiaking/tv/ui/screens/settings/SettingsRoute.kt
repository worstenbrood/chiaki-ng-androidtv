// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.common.db.RegisteredHost

@Composable
fun SettingsRoute(
	preferences: Preferences,
	database: AppDatabase,
	onBack: () -> Unit,
	onShowLogs: () -> Unit,
	onManageHosts: () -> Unit,
	onExportSettings: () -> Unit,
	onImportSettings: () -> Unit
)
{
	val registeredHosts = remember { mutableStateOf<List<RegisteredHost>>(emptyList()) }
	LaunchedEffect(Unit) {
		database.registeredHostDao().getAll().collect { registeredHosts.value = it }
	}
	SettingsScreen(
		preferences = preferences,
		registeredHostCount = registeredHosts.value.size,
		registeredHosts = registeredHosts.value,
		onBack = onBack,
		onShowLogs = onShowLogs,
		onManageHosts = onManageHosts,
		onExportSettings = onExportSettings,
		onImportSettings = onImportSettings
	)
}
