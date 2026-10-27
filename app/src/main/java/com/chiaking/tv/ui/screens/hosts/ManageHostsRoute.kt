package com.chiaking.tv.ui.screens.hosts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.ui.navigation.rememberRouteViewModel

@Composable
fun ManageHostsRoute(database: AppDatabase, onBack: () -> Unit) {
    val viewModel = rememberRouteViewModel { ManageHostsViewModel(database) }
    val hosts by viewModel.hosts.collectAsState()
    ManageHostsScreen(hosts, viewModel::delete, viewModel::setPin, onBack)
}
