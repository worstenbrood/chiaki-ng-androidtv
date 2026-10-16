// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.connect

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.DiscoveredDisplayHost
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.common.LastSeenHosts
import com.chiaking.tv.common.MacAddress
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.common.db.RegisteredHost
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.HolepunchDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the "start a local connection by MAC" flows: the TV-channel / home-card deep link and the
 * startup auto-connect. Both resolve the MAC to a registered host, wait briefly for discovery to
 * report the console, then ask the Activity to connect ([onConnectReady]) or wake ([onWakeRequest]).
 */
class LocalConnectController(
	private val context: Context,
	private val scope: CoroutineScope,
	private val database: AppDatabase,
	private val preferences: Preferences,
)
{
	companion object
	{
		const val EXTRA_AUTO_CONNECT_MAC = "com.chiaking.tv.AUTO_CONNECT_MAC"
		const val EXTRA_REMOTE_CONNECT_DUID = "com.chiaking.tv.REMOTE_CONNECT_DUID"
		const val EXTRA_REMOTE_CONNECT_PS5 = "com.chiaking.tv.REMOTE_CONNECT_PS5"
		const val EXTRA_REMOTE_CONNECT_NAME = "com.chiaking.tv.REMOTE_CONNECT_NAME"
		const val AUTO_CONNECT_DISCOVER_GRACE_MS = 5000L
		const val DEEP_LINK_DISCOVER_TIMEOUT_MS = 15000L
		const val DISCOVERY_POLL_MS = 500L
	}

	var pendingDeepLinkMac by mutableStateOf<MacAddress?>(null)
		private set

	var pendingRemoteDevice by mutableStateOf<HolepunchDevice?>(null)
		private set

	private var autoConnectTriggered = false
	private var deepLinkRequested = false

	/** Called with the registered host + fresh discovery info when a stream can start directly. */
	var onConnectReady: (RegisteredHost, DiscoveryHost) -> Unit = { _, _ -> }

	/** Called with a display host to wake (console is in rest mode). */
	var onWakeRequest: (DiscoveryManager, DisplayHost, String) -> Unit = { _, _, _ -> }

	/** Called with a PSN device when the deep link asked for a Remote Play over Internet session. */
	var onRemoteConnectRequest: (HolepunchDevice) -> Unit = { }

	fun handleDeepLink(intent: Intent?)
	{
		if(intent == null)
			return
		val duid = intent.getStringExtra(EXTRA_REMOTE_CONNECT_DUID)
		if(duid != null)
		{
			intent.removeExtra(EXTRA_REMOTE_CONNECT_DUID)
			val ps5 = intent.getBooleanExtra(EXTRA_REMOTE_CONNECT_PS5, true)
			val name = intent.getStringExtra(EXTRA_REMOTE_CONNECT_NAME) ?: ""
			deepLinkRequested = true
			pendingRemoteDevice = HolepunchDevice(ps5, true, name, duid)
			return
		}
		val macStr = intent.getStringExtra(EXTRA_AUTO_CONNECT_MAC) ?: return
		intent.removeExtra(EXTRA_AUTO_CONNECT_MAC)
		val mac = try { MacAddress(macStr) } catch(e: IllegalArgumentException) { return }
		deepLinkRequested = true
		pendingDeepLinkMac = mac
	}

	fun connectDeepLink(discoveryManager: DiscoveryManager)
	{
		pendingRemoteDevice?.let { device ->
			pendingRemoteDevice = null
			onRemoteConnectRequest(device)
			return
		}
		val mac = pendingDeepLinkMac ?: return
		pendingDeepLinkMac = null
		connectByMac(discoveryManager, mac, DEEP_LINK_DISCOVER_TIMEOUT_MS, includeLastSeen = true,
			wakeMessage = context.getString(com.chiaking.tv.R.string.connecting_to))
	}

	fun autoConnect(discoveryManager: DiscoveryManager)
	{
		if(autoConnectTriggered || deepLinkRequested)
			return
		if(!preferences.autoConnectEnabled)
			return
		autoConnectTriggered = true
		val macStr = preferences.autoConnectMac
		if(macStr.isEmpty())
			return
		val mac = try { MacAddress(macStr) } catch(e: IllegalArgumentException) { return }
		connectByMac(discoveryManager, mac, AUTO_CONNECT_DISCOVER_GRACE_MS, includeLastSeen = false,
			wakeMessage = context.getString(com.chiaking.tv.R.string.auto_connecting_to))
	}

	private fun connectByMac(discoveryManager: DiscoveryManager, mac: MacAddress,
		graceMs: Long, includeLastSeen: Boolean, wakeMessage: String)
	{
		scope.launch {
			val reg = withContext(Dispatchers.IO) { database.registeredHostDao().getByMac(mac) }
				?: return@launch
			val found = awaitDiscoveredHost(discoveryManager, mac, graceMs, includeLastSeen)
				?: return@launch
			if(found.state == DiscoveryHost.State.READY)
				onConnectReady(reg, found)
			else if(found.state == DiscoveryHost.State.STANDBY)
				onWakeRequest(discoveryManager, DiscoveredDisplayHost(reg, found), wakeMessage)
		}
	}

	private suspend fun awaitDiscoveredHost(discoveryManager: DiscoveryManager, mac: MacAddress,
		graceMs: Long, includeLastSeen: Boolean): DiscoveryHost?
	{
		val deadline = SystemClock.elapsedRealtime() + graceMs
		while(true)
		{
			val found = discoveryManager.discoveredHosts.value.firstOrNull { it.serverMac == mac }
				?: if(includeLastSeen)
					LastSeenHosts.hosts.value[mac]?.takeIf { !it.hostAddr.isNullOrEmpty() }
				else
					null
			if(found != null)
				return found
			if(SystemClock.elapsedRealtime() >= deadline)
				return null
			delay(DISCOVERY_POLL_MS)
		}
	}
}
