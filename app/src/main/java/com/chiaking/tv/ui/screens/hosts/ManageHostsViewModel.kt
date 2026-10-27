package com.chiaking.tv.ui.screens.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.db.RegisteredHost
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageHostsViewModel(private val database: AppDatabase) : ViewModel() {
    val hosts = database.registeredHostDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(host: RegisteredHost) { viewModelScope.launch { database.registeredHostDao().delete(host) } }
    fun setPin(host: RegisteredHost, pin: String?) {
        viewModelScope.launch { database.registeredHostDao().setPin(host.id, pin) }
    }
}
