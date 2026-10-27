// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import com.chiaking.tv.common.MacAddress

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
	open fun importAll(registeredHosts: List<RegisteredHost>, manualHosts: List<ManualHostImport>, replace: Boolean)
	{
		if(replace)
		{
			deleteAllManualHosts()
			deleteAllRegisteredHosts()
		}

		insertRegisteredHosts(registeredHosts)

		val macs = manualHosts.mapNotNull { it.serverMac }
		val idMacs =
			if(macs.isNotEmpty())
				registeredHostsByMac(macs)
			else
				listOf()

		insertManualHosts(
			manualHosts.map {
				ManualHost(
					host = it.host,
					registeredHost = idMacs.firstOrNull { regHost -> regHost.mac == it.serverMac }?.id
				)
		})
	}
}
