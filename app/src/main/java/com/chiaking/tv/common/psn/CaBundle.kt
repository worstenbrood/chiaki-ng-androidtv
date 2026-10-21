// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.psn

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The native curl build (holepunch listDevices / session / websocket) has no system
 * CA bundle on Android. curl was compiled with CURL_CA_BUNDLE pointing at exactly this
 * path (see native/CMakeLists.txt).
 *
 * ensure() extracts the bundled Mozilla cacert.pem only when no file exists yet, so a
 * previously downloaded newer bundle survives restarts. update() refreshes it in the
 * background from curl.se with a conditional GET (ETag / Last-Modified); a newer bundle
 * is written atomically. If the download fails and no file exists, the bundled one is used.
 */
object CaBundle
{
	private const val ASSET = "cacert.pem"
	private const val FILE_NAME = "ca-bundle.pem"
	private const val SOURCE_URL = "https://curl.se/ca/cacert.pem"
	private const val PREFS = "ca_bundle"
	private const val KEY_ETAG = "etag"
	private const val KEY_LAST_MODIFIED = "last_modified"
	private const val HTTP_TIMEOUT_MS = 10000
	private const val MIN_VALID_BYTES = 20000

	@Volatile
	private var ensured = false

	private fun file(context: Context) = File(context.filesDir, FILE_NAME)

	fun ensure(context: Context)
	{
		if(ensured)
			return
		synchronized(this)
		{
			if(ensured)
				return
			val dst = file(context)
			if(dst.length() == 0L)
				extractBundled(context, dst)
			else
				android.util.Log.i("CaBundle", "CA bundle at ${dst.absolutePath} (${dst.length()} bytes)")
			ensured = true
		}
	}

	/** Fire-and-forget background refresh; safe to call on every start. */
	fun update(context: Context)
	{
		Thread {
			val dst = file(context)
			try
			{
				val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				val conn = URL(SOURCE_URL).openConnection() as HttpURLConnection
				try
				{
					conn.requestMethod = "GET"
					conn.connectTimeout = HTTP_TIMEOUT_MS
					conn.readTimeout = HTTP_TIMEOUT_MS
					prefs.getString(KEY_ETAG, null)?.let { conn.setRequestProperty("If-None-Match", it) }
					prefs.getString(KEY_LAST_MODIFIED, null)?.let { conn.setRequestProperty("If-Modified-Since", it) }
					val code = conn.responseCode
					android.util.Log.i("CaBundle", "CA update check -> HTTP $code")
					if(code == HttpURLConnection.HTTP_NOT_MODIFIED)
						return@Thread
					if(code !in 200..299)
					{
						fallback(context, dst)
						return@Thread
					}
					val data = conn.inputStream.use { it.readBytes() }
					val etag = conn.getHeaderField("ETag")
					val lastModified = conn.getHeaderField("Last-Modified")
					if(data.size < MIN_VALID_BYTES || !String(data, 0, minOf(data.size, 4096)).contains("BEGIN CERTIFICATE"))
					{
						android.util.Log.w("CaBundle", "CA download failed sanity check (${data.size} bytes)")
						fallback(context, dst)
						return@Thread
					}
					val current = if(dst.exists()) dst.readBytes() else ByteArray(0)
					if(!data.contentEquals(current))
					{
						val tmp = File(dst.parentFile, "$FILE_NAME.tmp")
						tmp.writeBytes(data)
						if(!tmp.renameTo(dst))
						{
							dst.delete()
							tmp.renameTo(dst)
						}
						android.util.Log.i("CaBundle", "CA bundle updated to ${data.size} bytes")
					}
					prefs.edit()
						.putString(KEY_ETAG, etag)
						.putString(KEY_LAST_MODIFIED, lastModified)
						.apply()
				}
				finally
				{
					conn.disconnect()
				}
			}
			catch(e: Exception)
			{
				android.util.Log.w("CaBundle", "CA update failed", e)
				fallback(context, dst)
			}
		}.start()
	}

	private fun fallback(context: Context, dst: File)
	{
		if(dst.length() == 0L)
			extractBundled(context, dst)
	}

	private fun extractBundled(context: Context, dst: File)
	{
		try
		{
			context.assets.open(ASSET).use { input -> dst.outputStream().use { input.copyTo(it) } }
			android.util.Log.i("CaBundle", "CA bundle at ${dst.absolutePath} (${dst.length()} bytes)")
		}
		catch(e: Exception)
		{
			android.util.Log.e("CaBundle", "Failed to extract CA bundle", e)
		}
	}
}
