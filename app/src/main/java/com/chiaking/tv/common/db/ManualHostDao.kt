// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualHostDao
{
	@Query("SELECT * FROM manual_host WHERE id = :id")
	suspend fun getById(id: Long): ManualHost

	@Query("""SELECT
			manual_host.id as manual_host_id,
			manual_host.host as manual_host_host,
			manual_host.registered_host as manual_host_registered_host,
			registered_host.*
		FROM manual_host LEFT OUTER JOIN registered_host ON manual_host.registered_host = registered_host.id WHERE manual_host.id = :id""")
	suspend fun getByIdWithRegisteredHost(id: Long): ManualHostAndRegisteredHost

	@Query("SELECT * FROM manual_host")
	fun getAll(): Flow<List<ManualHost>>

	@Query("SELECT * FROM manual_host")
	suspend fun getAllOnce(): List<ManualHost>

	@Query("UPDATE manual_host SET registered_host = :registeredHostId WHERE id = :manualHostId")
	suspend fun assignRegisteredHost(manualHostId: Long, registeredHostId: Long?)

	@Insert
	suspend fun insert(host: ManualHost)

	@Delete
	suspend fun delete(host: ManualHost)

	@Update
	suspend fun update(host: ManualHost)
}
