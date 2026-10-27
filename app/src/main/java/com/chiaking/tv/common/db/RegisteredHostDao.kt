// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import com.chiaking.tv.common.MacAddress
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RegisteredHostDao
{
	@Query("SELECT * FROM registered_host")
	fun getAll(): Flow<List<RegisteredHost>>

	@Query("SELECT * FROM registered_host")
	suspend fun getAllOnce(): List<RegisteredHost>

	@Query("SELECT * FROM registered_host WHERE server_mac == :mac LIMIT 1")
	suspend fun getByMac(mac: MacAddress): RegisteredHost?

	@Query("DELETE FROM registered_host WHERE server_mac == :mac")
	suspend fun deleteByMac(mac: MacAddress)

	@Delete
	suspend fun delete(host: RegisteredHost)

	@Query("SELECT COUNT(*) FROM registered_host")
	fun count(): Flow<Int>

	@Insert
	suspend fun insert(host: RegisteredHost): Long

	@Update
	suspend fun update(host: RegisteredHost)

	@Query("UPDATE registered_host SET rp_pin = :pin WHERE id == :id")
	suspend fun setPin(id: Long, pin: String?)
}
