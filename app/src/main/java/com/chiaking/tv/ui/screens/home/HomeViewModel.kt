// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiaking.tv.common.*
import com.chiaking.tv.common.db.*
import com.chiaking.tv.common.settings.*
import com.chiaking.tv.common.logging.*
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.HolepunchDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(context: Context, val database: AppDatabase, val preferences: Preferences): ViewModel()
{
	companion object
	{
		private const val SUBSCRIBE_TIMEOUT_MS = 5000L
	}

	private val appContext = context.applicationContext
	val discoveryManager = DiscoveryManager().also {
		it.active = preferences.discoveryEnabled
		viewModelScope.launch {
			it.discoveryActive.collect { active ->
				preferences.discoveryEnabled = active
			}
		}
	}

	init
	{
		viewModelScope.launch {
			discoveryManager.discoveredHosts.collect { hosts ->
				LastSeenHosts.update(hosts)
			}
		}
	}

	val displayHosts by lazy {
		combine(
			database.manualHostDao().getAll(),
			database.registeredHostDao().getAll(),
			discoveryManager.discoveredHosts,
			LastSeenHosts.hosts
		) { manualHosts, registeredHosts, discoveredHosts, lastSeen ->
			val macRegisteredHosts = registeredHosts.associateBy { it.serverMac }
			val idRegisteredHosts = registeredHosts.associateBy { it.id }
			val discoveredMacs = discoveredHosts.mapNotNull { it.serverMac }.toSet()
			discoveredHosts.map {
				DiscoveredDisplayHost(it.serverMac?.let { mac -> macRegisteredHosts[mac] }, it)
			} +
			registeredHosts.mapNotNull { reg ->
				if(reg.serverMac !in discoveredMacs)
					lastSeen[reg.serverMac]?.let { DiscoveredDisplayHost(reg, it, cached = true) }
				else
					null
			} +
			manualHosts.map {
				ManualDisplayHost(it.registeredHost?.let { id -> idRegisteredHosts[id] }, it)
			}
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())
	}

	val discoveryActive by lazy {
		discoveryManager.discoveryActive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), false)
	}

	private var psnRefreshJob: Job? = null
    private var refreshGeneration = 0
    val psnLoggedIn = MutableStateFlow(PsnAccountRepository.get(appContext).isLoggedIn)
	val psnDevices = MutableStateFlow<List<HolepunchDevice>>(emptyList())
	val psnLoading = MutableStateFlow(false)
	val psnDeviceCount = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            PsnAccountRepository.get(appContext).state.map { it.connectionAccountId }.distinctUntilChanged().collect { rid ->
                psnRefreshJob?.cancel()
                refreshGeneration++
                psnLoading.value = false
                psnLoggedIn.value = rid != null
                psnDevices.value = emptyList()
                psnDeviceCount.value = null
                if(rid != null) refreshPsnDevices()
            }
        }
    }
	fun refreshPsnLoginState()
	{
		psnLoggedIn.value = PsnAccountRepository.get(appContext).isLoggedIn
	}

	fun refreshPsnDevices()
	{
		if(psnLoading.value)
			return
		val store = PsnAccountRepository.get(appContext)
        val rid = store.accountId ?: return
        val generation = ++refreshGeneration
        psnLoading.value = true
        psnRefreshJob = viewModelScope.launch {
			try
			{
				val devices = com.chiaking.tv.common.psn.PsnDeviceRepository.list(appContext, rid)
                if(generation != refreshGeneration || store.accountId != rid) return@launch
				if(devices == null)
				{
					psnDevices.value = emptyList()
					psnDeviceCount.value = -1
					return@launch
				}
				val remote = devices.filter { it.remotePlayEnabled }
				psnDevices.value = remote
				psnDeviceCount.value = remote.size
			}
			catch(e: CancellationException) { throw e }
            catch(e: Exception)
			{
				if(generation != refreshGeneration || store.accountId != rid) return@launch
                android.util.Log.e("HomeViewModel", "PSN device refresh failed", e)
				psnDevices.value = emptyList()
				psnDeviceCount.value = -1
			}
			finally
			{
				if(generation == refreshGeneration) psnLoading.value = false
			}
		}
	}

	fun deleteManualHost(manualHost: ManualHost)
	{
		viewModelScope.launch(Dispatchers.IO) {
			try {
				database.manualHostDao().delete(manualHost)
			} catch(_: Exception) {}
		}
	}

	fun addManualHost(host: String)
	{
		viewModelScope.launch(Dispatchers.IO) {
			database.manualHostDao().insert(ManualHost(host = host, registeredHost = null))
		}
	}

	fun updateManualHost(manualHost: ManualHost, host: String)
	{
		viewModelScope.launch(Dispatchers.IO) {
			database.manualHostDao().update(manualHost.copy(host = host))
		}
	}

	override fun onCleared()
	{
		super.onCleared()
		discoveryManager.dispose()
	}
}
