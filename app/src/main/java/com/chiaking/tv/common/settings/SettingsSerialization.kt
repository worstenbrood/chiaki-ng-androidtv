// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.settings

import com.chiaking.tv.common.MacAddressJsonAdapter

import android.util.Base64
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException

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

suspend fun exportSettings(settings: SerializedSettings): String = withContext(Dispatchers.IO) {
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
			else -> reader.skipValue()
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
