// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import com.chiaking.tv.common.MacAddress
import com.chiaking.tv.common.settings.SerializedSettings

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ImportDao
{
	@Insert
	abstract fun insertRegisteredHosts(hosts: List<RegisteredHost>)

	@Insert
	abstract fun insertManualHosts(hosts: List<ManualHost>)

	@Query("DELETE FROM manual_host")
	abstract fun deleteAllManualHosts()

	@Query("DELETE FROM registered_host")
	abstract fun deleteAllRegisteredHosts()

	class IdWithMac(val id: Long, val mac: MacAddress)

	@Query("SELECT id, server_mac AS mac FROM registered_host WHERE server_mac IN (:macs)")
	abstract fun registeredHostsByMac(macs: List<MacAddress>): List<IdWithMac>

	@Transaction
	open fun importAll(settings: SerializedSettings, replace: Boolean)
	{
		if(replace)
		{
			deleteAllManualHosts()
			deleteAllRegisteredHosts()
		}

		insertRegisteredHosts(
			settings.registeredHosts.map {
				RegisteredHost(
					target = it.target,
					apSsid = it.apSsid,
					apBssid = it.apBssid,
					apKey = it.apKey,
					apName = it.apName,
					serverMac = it.serverMac,
					serverNickname = it.serverNickname,
					rpRegistKey = it.rpRegistKey,
					rpKeyType = it.rpKeyType,
					rpKey = it.rpKey
				)
		})

		val macs = settings.manualHosts.mapNotNull { it.serverMac }
		val idMacs =
			if(macs.isNotEmpty())
				registeredHostsByMac(macs)
			else
				listOf()

		insertManualHosts(
			settings.manualHosts.map {
				ManualHost(
					host = it.host,
					registeredHost = idMacs.firstOrNull { regHost -> regHost.mac == it.serverMac }?.id
				)
		})
	}
}
