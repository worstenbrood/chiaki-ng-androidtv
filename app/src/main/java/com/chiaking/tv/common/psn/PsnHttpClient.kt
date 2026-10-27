// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.common.psn

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** PSN JSON transport. Never logs URLs, headers or bodies: they can contain tokens. */
internal class PsnHttpClient {
    fun request(
        url: String,
        authorization: String,
        body: String? = null,
        timeoutMs: Int = 15000,
        operation: String = "OAuth",
    ): JSONObject {
        val destination = URL(url)
        require(destination.protocol == "https" && destination.userInfo == null)
        val conn = destination.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = if(body == null) "GET" else "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            // Do not forward credentials to a redirected destination.
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", authorization)
            conn.setRequestProperty("Accept", "application/json")
            if(body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            Log.i("PsnAuth", "PSN $operation -> $status")
            if(status !in 200..299) {
                // Error bodies may echo credentials; keep diagnostics limited to status.
                conn.errorStream?.close()
                throw IOException("PSN $operation HTTP $status")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
