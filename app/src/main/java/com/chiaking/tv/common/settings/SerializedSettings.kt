// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.settings

import com.chiaking.tv.common.db.AppDatabase

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.first

@JsonClass(generateAdapter = true)
data class SerializedSettings(
	@Json(name = "registered_hosts") val registeredHosts: List<SerializedRegisteredHost>,
	@Json(name = "manual_hosts") val manualHosts: List<SerializedManualHost>
)
{
	companion object
	{
		suspend fun fromDatabase(db: AppDatabase): SerializedSettings
		{
			val registeredHosts = db.registeredHostDao().getAll().first()
			val manualHosts = db.manualHostDao().getAll().first()
			return SerializedSettings(
				registeredHosts.map { SerializedRegisteredHost(it) },
				manualHosts.map { manualHost ->
					SerializedManualHost(
						manualHost.host,
						manualHost.registeredHost?.let { registeredHostId ->
							registeredHosts.firstOrNull { it.id == registeredHostId }
						}?.serverMac
					)
				}
			)
		}
	}
}
