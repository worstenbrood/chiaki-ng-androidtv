// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.util.Base64
import androidx.room.*
import com.chiaking.tv.lib.Target
import com.squareup.moshi.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.source
import java.io.IOException

@JsonClass(generateAdapter = true)
class SerializedRegisteredHost(
	@Json(name = "target") val target: Target,
	@Json(name = "ap_ssid") val apSsid: String?,
	@Json(name = "ap_bssid") val apBssid: String?,
	@Json(name = "ap_key") val apKey: String?,
	@Json(name = "ap_name") val apName: String?,
	@Json(name = "server_mac") val serverMac: MacAddress,
	@Json(name = "server_nickname") val serverNickname: String?,
	@Json(name = "rp_regist_key") val rpRegistKey: ByteArray,
	@Json(name = "rp_key_type") val rpKeyType: Int,
	@Json(name = "rp_key") val rpKey: ByteArray
){
	constructor(registeredHost: RegisteredHost) : this(
		registeredHost.target,
		registeredHost.apSsid,
		registeredHost.apBssid,
		registeredHost.apKey,
		registeredHost.apName,
		registeredHost.serverMac,
		registeredHost.serverNickname,
		registeredHost.rpRegistKey,
		registeredHost.rpKeyType,
		registeredHost.rpKey
	)
}

@JsonClass(generateAdapter = true)
class SerializedManualHost(
	@Json(name = "host") val host: String,
	@Json(name = "server_mac") val serverMac: MacAddress?
)

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

private class ByteArrayJsonAdapter
{
	@ToJson fun toJson(byteArray: ByteArray) = Base64.encodeToString(byteArray, Base64.NO_WRAP)
	@FromJson fun fromJson(string: String) = Base64.decode(string, Base64.DEFAULT)
}

private fun moshi() =
	Moshi.Builder()
	.add(MacAddressJsonAdapter())
	.add(ByteArrayJsonAdapter())
	.build()

private fun Moshi.serializedSettingsAdapter() =
	adapter(SerializedSettings::class.java)
	.serializeNulls()

private const val KEY_FORMAT = "format"
private const val FORMAT = "chiaki-settings"
private const val KEY_VERSION = "version"
private const val VERSION = 2
private const val KEY_SETTINGS = "settings"

suspend fun exportAllSettings(db: AppDatabase): String = withContext(Dispatchers.IO) {
	val settings = SerializedSettings.fromDatabase(db)
	val buffer = Buffer()
	val writer = JsonWriter.of(buffer)
	val adapter = moshi().serializedSettingsAdapter()
	writer.indent = "  "
	writer.
		beginObject()
		.name(KEY_FORMAT).value(FORMAT)
		.name(KEY_VERSION).value(VERSION)
	writer.name(KEY_SETTINGS)
	adapter.toJson(writer, settings)
	writer.endObject()
	buffer.readUtf8()
}

class SettingsImportException(message: String): IOException(message)

suspend fun parseSettingsImport(source: okio.BufferedSource): SerializedSettings = withContext(Dispatchers.IO) {
	val reader = JsonReader.of(source)
	val adapter = moshi().serializedSettingsAdapter()

	var format: String? = null
	var version: Int? = null
	var settingsValue: Any? = null

	reader.beginObject()
	while(reader.hasNext())
	{
		when(reader.nextName())
		{
			KEY_FORMAT -> format = reader.nextString()
			KEY_VERSION -> version = reader.nextInt()
			KEY_SETTINGS -> settingsValue = reader.readJsonValue()
		}
	}
	reader.endObject()

	if(format == null || version == null || settingsValue == null)
		throw SettingsImportException("Missing format, version or settings from JSON")
	if(format != FORMAT)
		throw SettingsImportException("Value of format is invalid")
	if(version != VERSION)
		throw SettingsImportException("Value of version is invalid")

	try {
		adapter.fromJsonValue(settingsValue) ?: throw SettingsImportException("Failed to parse Settings JSON")
	} catch(e: JsonDataException) {
		throw SettingsImportException(e.message ?: "Failed to parse Settings JSON")
	}
}

suspend fun importSettings(db: AppDatabase, settings: SerializedSettings) = withContext(Dispatchers.IO) {
	db.importDao().importAll(settings)
}

@Dao
abstract class ImportDao
{
	@Insert
	abstract fun insertRegisteredHosts(hosts: List<RegisteredHost>)

	@Insert
	abstract fun insertManualHosts(hosts: List<ManualHost>)

	class IdWithMac(val id: Long, val mac: MacAddress)

	@Query("SELECT id, server_mac AS mac FROM registered_host WHERE server_mac IN (:macs)")
	abstract fun registeredHostsByMac(macs: List<MacAddress>): List<IdWithMac>

	@Transaction
	open fun importAll(settings: SerializedSettings)
	{
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
