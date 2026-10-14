// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.psn

import android.content.Context
import java.io.File

/**
 * The native curl build (holepunch listDevices / session / websocket) has no system
 * CA bundle on Android. Extracts the bundled Mozilla cacert.pem to filesDir; curl was
 * compiled with CURL_CA_BUNDLE pointing at exactly this path (see native/CMakeLists.txt).
 */
object CaBundle
{
	private const val ASSET = "cacert.pem"
	private const val FILE_NAME = "ca-bundle.pem"

	@Volatile
	private var ensured = false

	fun ensure(context: Context)
	{
		if(ensured)
			return
		synchronized(this)
		{
			if(ensured)
				return
			try
			{
				val dst = File(context.filesDir, FILE_NAME)
				context.assets.open(ASSET).use { input -> dst.outputStream().use { input.copyTo(it) } }
				ensured = true
				android.util.Log.i("CaBundle", "CA bundle at ${dst.absolutePath} (${dst.length()} bytes)")
			}
			catch(e: Exception)
			{
				android.util.Log.e("CaBundle", "Failed to extract CA bundle", e)
			}
		}
	}
}
