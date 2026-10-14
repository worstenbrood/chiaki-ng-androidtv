// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiaking.tv.common.*
import com.chiaking.tv.common.psn.PsnTokenStore
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.Holepunch
import com.metallic.chiaki.lib.HolepunchDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(context: Context, val database: AppDatabase, val preferences: Preferences): ViewModel()
{
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
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
	}

	val discoveryActive by lazy {
		discoveryManager.discoveryActive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
	}

	val registeredHostCount = database.registeredHostDao().count()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

	val psnLoggedIn = MutableStateFlow(PsnTokenStore.get(appContext).isLoggedIn)
	val psnDevices = MutableStateFlow<List<HolepunchDevice>>(emptyList())
	val psnLoading = MutableStateFlow(false)
	val psnDeviceCount = MutableStateFlow<Int?>(null)

	fun refreshPsnLoginState()
	{
		psnLoggedIn.value = PsnTokenStore.get(appContext).isLoggedIn
	}

	fun refreshPsnDevices()
	{
		if(psnLoading.value)
			return
		viewModelScope.launch {
			psnLoading.value = true
			try
			{
				val store = PsnTokenStore.get(appContext)
				psnLoggedIn.value = store.isLoggedIn
				val token = withContext(Dispatchers.IO) { store.ensureFreshToken() }
				if(token == null)
				{
					psnDevices.value = emptyList()
					psnDeviceCount.value = -1
					return@launch
				}
				// NOTE: chiaki_holepunch_list_devices only supports PS5 upstream.
				val devices = withContext(Dispatchers.IO) {
					val ps5 = Holepunch.listDevices(token, true)
					if(!ps5.errorCode.isSuccess)
					{
						android.util.Log.e("MainViewModel", "PSN listDevices failed: ps5=${ps5.errorCode}")
						return@withContext null
					}
					ps5.devices
				}
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
			catch(e: Exception)
			{
				android.util.Log.e("MainViewModel", "PSN device refresh failed", e)
				psnDevices.value = emptyList()
				psnDeviceCount.value = -1
			}
			finally
			{
				psnLoading.value = false
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

	fun deleteRegisteredHost(host: RegisteredHost)
	{
		viewModelScope.launch(Dispatchers.IO) {
			try {
				database.registeredHostDao().delete(host)
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
