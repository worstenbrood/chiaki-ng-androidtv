// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiaking.tv.common.*
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.DiscoveryHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(val database: AppDatabase, val preferences: Preferences): ViewModel()
{
	val discoveryManager = DiscoveryManager().also {
		it.active = preferences.discoveryEnabled
		viewModelScope.launch {
			it.discoveryActive.collect { active ->
				preferences.discoveryEnabled = active
			}
		}
	}

	private val lastSeenHosts = MutableStateFlow<Map<MacAddress, DiscoveryHost>>(emptyMap())

	init
	{
		viewModelScope.launch {
			discoveryManager.discoveredHosts.collect { hosts ->
				if(hosts.isEmpty())
					return@collect
				val cur = lastSeenHosts.value.toMutableMap()
				hosts.forEach { h -> h.serverMac?.let { cur[it] = h } }
				lastSeenHosts.value = cur
			}
		}
	}

	val displayHosts by lazy {
		combine(
			database.manualHostDao().getAll(),
			database.registeredHostDao().getAll(),
			discoveryManager.discoveredHosts,
			lastSeenHosts
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
