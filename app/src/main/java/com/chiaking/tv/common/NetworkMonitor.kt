// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
			callbackFlow {
				val callback = object : ConnectivityManager.NetworkCallback()
				{
					override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities)
					{
						if(network == cm.activeNetwork &&
							capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
							trySend(true)
					}
				}
				cm.registerDefaultNetworkCallback(callback)
				// Cover validation between the initial check and callback registration.
				if(isValidated)
					trySend(true)
				awaitClose { cm.unregisterNetworkCallback(callback) }
			}.first()
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
