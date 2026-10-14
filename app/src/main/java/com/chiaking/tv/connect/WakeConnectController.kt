// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.connect

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chiaking.tv.common.DiscoveredDisplayHost
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.DiscoveryHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the wake-on-LAN flow: sends the wakeup packet, then polls discovery until the console
 * reports READY (or the timeout expires) and hands the ready host to the [onReady] callback.
 * Exposes [wakingHost] / [bannerText] as Compose state for the home-screen banner.
 */
class WakeConnectController(private val scope: CoroutineScope)
{
	companion object
	{
		const val WAKE_TIMEOUT_MS = 25000L
		const val DISCOVERY_POLL_MS = 500L
	}

	var wakingHost by mutableStateOf<DisplayHost?>(null)
		private set

	var bannerText by mutableStateOf<String?>(null)
		private set

	private var job: Job? = null

	fun toggle(discoveryManager: DiscoveryManager, host: DisplayHost, bannerText: String,
		onReady: (DiscoveryHost) -> Unit)
	{
		val reg = host.registeredHost ?: return
		if(wakingHost?.host == host.host)
		{
			cancel()
			return
		}
		cancel()
		discoveryManager.sendWakeup(host.host, reg.rpRegistKey, reg.target.isPS5)
		val mac = (host as? DiscoveredDisplayHost)?.discoveredHost?.serverMac ?: return
		wakingHost = host
		this.bannerText = bannerText
		job = scope.launch {
			val deadline = SystemClock.elapsedRealtime() + WAKE_TIMEOUT_MS
			var ready: DiscoveryHost? = null
			while(ready == null && SystemClock.elapsedRealtime() < deadline)
			{
				ready = discoveryManager.discoveredHosts.value.firstOrNull {
					it.serverMac == mac && it.state == DiscoveryHost.State.READY
				}
				if(ready == null)
					delay(DISCOVERY_POLL_MS)
			}
			wakingHost = null
			this@WakeConnectController.bannerText = null
			job = null
			if(ready != null)
				onReady(ready)
		}
	}

	fun cancel()
	{
		job?.cancel()
		job = null
		wakingHost = null
		bannerText = null
	}
}
