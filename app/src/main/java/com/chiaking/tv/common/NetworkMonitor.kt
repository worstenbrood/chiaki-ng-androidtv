// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Caches the ConnectivityManager and exposes network-readiness checks.
 * VALIDATED means the system's connectivity probe succeeded: internet + DNS work.
 */
class NetworkMonitor private constructor(context: Context)
{
	private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

	val isValidated: Boolean
		get() = cm.getNetworkCapabilities(cm.activeNetwork)
			?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

	/**
	 * Suspends until the active network is VALIDATED (internet + DNS confirmed), so cold-start
	 * fetches do not race the network stack. Returns false on timeout.
	 */
	suspend fun awaitValidated(timeoutMs: Long): Boolean
	{
		if(isValidated)
			return true
		return withTimeoutOrNull(timeoutMs) {
			suspendCancellableCoroutine { cont ->
				val callback = object : ConnectivityManager.NetworkCallback()
				{
					override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities)
					{
						if(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && cont.isActive)
							cont.resume(true)
					}
				}
				cont.invokeOnCancellation { try { cm.unregisterNetworkCallback(callback) } catch(e: Exception) { } }
				cm.registerNetworkCallback(
					NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
					callback
				)
			}
		} ?: false
	}

	companion object
	{
		@Volatile
		private var instance: NetworkMonitor? = null

		fun get(context: Context): NetworkMonitor =
			instance ?: synchronized(this) {
				instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
			}
	}
}
