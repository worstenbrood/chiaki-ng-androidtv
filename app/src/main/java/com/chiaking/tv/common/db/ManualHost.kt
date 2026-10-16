// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import androidx.room.*
import androidx.room.ForeignKey.Companion.SET_NULL

@Entity(tableName = "manual_host",
	foreignKeys = [
	ForeignKey(
		entity = RegisteredHost::class,
		parentColumns = ["id"],
		childColumns = ["registered_host"],
		onDelete = SET_NULL
	)
])
data class ManualHost(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val host: String,
	@ColumnInfo(name = "registered_host") val registeredHost: Long?
)
