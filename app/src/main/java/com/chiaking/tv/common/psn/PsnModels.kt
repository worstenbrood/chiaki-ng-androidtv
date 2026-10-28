package com.chiaking.tv.common.psn

import okio.ByteString.Companion.decodeBase64

data class PsnTokens(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)
data class PsnProfile(val onlineId: String, val avatarUrl: String)

/** Safe to retain in UI state: contains no access or refresh credentials. */
data class PsnAccountSummary(val rid: String, val onlineId: String, val avatarUrl: String)
data class PsnAccountsState(val connectionAccountId: String?, val accounts: List<PsnAccountSummary>, val connectionInUse: Boolean = false)

internal fun PsnAccount.summary() = PsnAccountSummary(rid, onlineId, avatarUrl)

interface PsnAuthentication {
    suspend fun exchangeCode(code: String): PsnTokens
    suspend fun refresh(refreshToken: String): PsnTokens
    suspend fun fetchAccountId(accessToken: String): String
}

interface PsnProfileService {
    suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile
}

internal fun decodePsnAccountId(rid: String): ByteArray? = rid.decodeBase64()?.toByteArray()?.takeIf { it.size == 8 }

internal fun psnUserId(rid: String): Long? = decodePsnAccountId(rid)?.let { bytes ->
    var value = 0L
    for(i in bytes.indices) value = value or ((bytes[i].toLong() and 255) shl (8 * i))
    value.takeIf { it != 0L }
}
