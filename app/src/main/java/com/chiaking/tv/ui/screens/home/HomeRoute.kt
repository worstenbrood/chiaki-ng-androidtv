// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.db.ManualHost
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.connect.LocalConnectController
import com.chiaking.tv.connect.RemoteConnectController
import com.chiaking.tv.connect.WakeConnectController
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.ui.components.dialogs.TvProgressDialog
import com.chiaking.tv.ui.navigation.rememberRouteViewModel
import com.chiaking.tv.ui.screens.hosts.ManualHostDialog
import com.metallic.chiaki.lib.HolepunchDevice

@Composable
fun HomeRoute(
    database: AppDatabase,
    preferences: Preferences,
    wakeController: WakeConnectController,
    remoteController: RemoteConnectController,
    localController: LocalConnectController,
    onStartStream: (DisplayHost) -> Unit,
    onWakeup: (DiscoveryManager, DisplayHost) -> Unit,
    onRemoteConnect: (HolepunchDevice) -> Unit,
    onRegist: (String?, Boolean?) -> Unit,
    onOpenSettings: () -> Unit
)
{
    val context = LocalContext.current.applicationContext
    val viewModel = rememberRouteViewModel { HomeViewModel(context, database, preferences) }
    val hosts by viewModel.displayHosts.collectAsState()
    val discoveryActive by viewModel.discoveryActive.collectAsState()
    val psnLoggedIn by viewModel.psnLoggedIn.collectAsState()
    val psnDevices by viewModel.psnDevices.collectAsState()
    val psnLoading by viewModel.psnLoading.collectAsState()
    val psnDeviceCount by viewModel.psnDeviceCount.collectAsState()
    var showManualHostDialog by remember { mutableStateOf(false) }
    var editingManualHost by remember { mutableStateOf<ManualHost?>(null) }

    LaunchedEffect(Unit) { viewModel.discoveryManager.resume() }
    LaunchedEffect(localController.pendingDeepLinkMac, localController.pendingRemoteDevice) { localController.connectDeepLink(viewModel.discoveryManager) }
    LaunchedEffect(Unit) { localController.autoConnect(viewModel.discoveryManager) }
    LaunchedEffect(Unit) { viewModel.refreshPsnLoginState(); if(viewModel.psnLoggedIn.value) viewModel.refreshPsnDevices() }
    DisposableEffect(Unit) { onDispose { viewModel.discoveryManager.pause() } }
    BackHandler(enabled = showManualHostDialog) { showManualHostDialog = false }

    Box {
        HomeScreen(hosts, discoveryActive,
            psnLoggedIn = psnLoggedIn,
            psnDevices = psnDevices,
            psnLoading = psnLoading,
            psnDeviceCount = psnDeviceCount
        ) { action ->
            when(action)
            {
                is ConnectHost -> onStartStream(action.host)
                is WakeupHost -> onWakeup(viewModel.discoveryManager, action.host)
                is ConnectRemoteConsole -> onRemoteConnect(action.device)
                RefreshPsnDevices -> viewModel.refreshPsnDevices()
                is EditManualHost -> {
                    editingManualHost = action.manualHost
                    showManualHostDialog = true
                }
                is DeleteManualHost -> viewModel.deleteManualHost(action.manualHost)
                RegisterNew -> onRegist(null, null)
                is RegisterHost -> onRegist(action.host, action.ps5)
                AddManualHost -> {
                    editingManualHost = null
                    showManualHostDialog = true
                }
                OpenSettings -> onOpenSettings()
                ToggleDiscovery -> viewModel.discoveryManager.active = !viewModel.discoveryManager.active
            }
        }
        if(showManualHostDialog)
            ManualHostDialog(
                initial = editingManualHost?.host ?: "",
                onDismiss = { showManualHostDialog = false },
                onSave = { value ->
                    val current = editingManualHost
                    if(current != null)
                        viewModel.updateManualHost(current, value)
                    else
                        viewModel.addManualHost(value)
                    showManualHostDialog = false
                }
            )
        if(wakeController.wakingHost != null)
            TvProgressDialog(wakeController.bannerText ?: "", wakeController::cancel)
        remoteController.connectingText?.let {
            TvProgressDialog(it, remoteController::cancel)
        }
    }
}
