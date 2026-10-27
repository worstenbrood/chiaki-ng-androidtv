// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.common.psn

import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
        authorizationHeader: String = "Authorization",
    ): JSONObject = JSONObject(read(url, authorization, body, timeoutMs, operation, authorizationHeader,
        1024 * 1024).toString(Charsets.UTF_8))

    /** Public images: intentionally has no credential argument. */
    fun downloadImage(url: String): ByteArray =
        read(url, null, null, 4000, "avatar", "Authorization", 1024 * 1024)

    private fun read(url: String, authorization: String?, body: String?, timeoutMs: Int,
        operation: String, authorizationHeader: String, maxBytes: Int): ByteArray {
        val destination = URL(url)
        require(destination.protocol == "https" && destination.userInfo == null)
        val conn = destination.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = if(body == null) "GET" else "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            // Do not forward credentials to a redirected destination.
            conn.instanceFollowRedirects = false
            require(authorizationHeader == "Authorization" || authorizationHeader == "X-NP-ACCESS-TOKEN")
            if(authorization != null) {
                conn.setRequestProperty(authorizationHeader, authorization)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
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
            return conn.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while(true) {
                    val count = stream.read(buffer)
                    if(count < 0) break
                    if(count > maxBytes - output.size()) throw IOException("PSN response too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}
