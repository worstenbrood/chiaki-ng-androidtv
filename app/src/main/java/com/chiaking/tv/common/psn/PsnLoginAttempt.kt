package com.chiaking.tv.common.psn

import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom

/** Binds a phone redirect to the device and dialog that initiated it. */
internal class PsnLoginAttempt(val deviceUid: String, val state: String = randomLoginSecret()) {
    val loginUrl get() = PsnAuth.loginUrl(deviceUid, state)

    fun phoneCode(input: String): String? {
        val parameters = redirectParameters(input) ?: return null
        if(parameters["state"] != state || parameters.containsKey("error")) return null
        return parameters["code"]?.takeIf { it.isNotBlank() && it.length <= 2048 }
    }
}

internal fun randomLoginSecret(): String = ByteArray(24).also { SecureRandom().nextBytes(it) }
    .joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun redirectParameters(input: String): Map<String, String>? = try {
    val uri = URI(input.trim())
    val expected = URI(PsnAuth.REDIRECT_PAGE)
    if(uri.scheme != expected.scheme || uri.host != expected.host || uri.path != expected.path ||
        uri.port != -1 || uri.userInfo != null || uri.fragment != null) null
    else {
        val pairs = uri.rawQuery.orEmpty().split('&').map {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            key to value
        }
        if(pairs.map { it.first }.distinct().size != pairs.size) null else pairs.toMap()
    }
} catch(_: Exception) { null }
