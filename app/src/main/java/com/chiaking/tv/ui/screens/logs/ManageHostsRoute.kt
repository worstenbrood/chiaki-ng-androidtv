// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiaking.tv.MainViewModel
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.common.db.RegisteredHost
import kotlinx.coroutines.launch

@Composable
fun ManageHostsRoute(database: AppDatabase, preferences: Preferences, onBack: () -> Unit)
{
	val context = LocalContext.current
	val viewModel = viewModel { MainViewModel(context, database, preferences) }
	val hosts = remember { mutableStateOf<List<RegisteredHost>>(emptyList()) }
	val scope = rememberCoroutineScope()
	LaunchedEffect(Unit) {
		database.registeredHostDao().getAll().collect { hosts.value = it }
	}
	ManageHostsScreen(hosts.value,
		{ viewModel.deleteRegisteredHost(it) },
		{ host, pin -> scope.launch { database.registeredHostDao().setPin(host.id, pin) } }) { onBack() }
}
