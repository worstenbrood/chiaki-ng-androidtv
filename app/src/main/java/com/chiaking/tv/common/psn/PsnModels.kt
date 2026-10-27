package com.chiaking.tv.common.psn

data class PsnTokens(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)
data class PsnProfile(val onlineId: String, val avatarUrl: String)

/** Safe to retain in UI state: contains no access or refresh credentials. */
data class PsnAccountSummary(val rid: String, val onlineId: String, val avatarUrl: String)
data class PsnAccountsState(val primaryId: String?, val accounts: List<PsnAccountSummary>)

internal fun PsnAccount.summary() = PsnAccountSummary(rid, onlineId, avatarUrl)

interface PsnAuthentication {
    suspend fun exchangeCode(code: String): PsnTokens
    suspend fun refresh(refreshToken: String): PsnTokens
    suspend fun fetchAccountId(accessToken: String): String
}

interface PsnProfileService {
    suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile
}
