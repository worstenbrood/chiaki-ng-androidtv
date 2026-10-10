// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.DiscoveryHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last-known discovery info per console MAC. Keeps registered consoles visible
 * (with their last state) when discovery temporarily drops them, and lets us
 * update the state on actions we perform ourselves (e.g. sleep via the app).
 */
object LastSeenHosts
{
	private val _hosts = MutableStateFlow<Map<MacAddress, DiscoveryHost>>(emptyMap())
	val hosts: StateFlow<Map<MacAddress, DiscoveryHost>> get() = _hosts.asStateFlow()

	fun update(discovered: List<DiscoveryHost>)
	{
		if(discovered.isEmpty())
			return
		val cur = _hosts.value.toMutableMap()
		discovered.forEach { h -> h.serverMac?.let { cur[it] = h } }
		_hosts.value = cur
	}

	fun markState(mac: MacAddress, state: DiscoveryHost.State)
	{
		val host = _hosts.value[mac] ?: return
		_hosts.value = _hosts.value + (mac to host.copy(state = state))
	}
}
