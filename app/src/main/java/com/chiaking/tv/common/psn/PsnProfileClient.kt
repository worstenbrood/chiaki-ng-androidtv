package com.chiaking.tv.common.psn

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remote Play profile API; authentication and credential persistence are separate concerns. */
object PsnProfileClient : PsnProfileService {
    private const val ACCOUNT_INFO_URL =
        "https://vl.api.np.km.playstation.net/vl/api/v1/mobile/users/me/info?languageSet=accountSet2"
    private const val USER_PROFILE_SERVICE_URL =
        "https://asm.np.community.playstation.net/asm/v1/apps/me/baseUrls/userProfile"
    private val http = PsnHttpClient()
	/** Mirrors Remote Play 9.5.0: account info first, then profile by online ID. */
	override suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile = withContext(Dispatchers.IO) {
		val info = http.request(
			ACCOUNT_INFO_URL,
			accessToken, timeoutMs = 5000, operation = "account info",
			authorizationHeader = "X-NP-ACCESS-TOKEN"
		)
		// Never attach a response to another stored account, even after a stale refresh.
		val accountId = info.getString("accountId").toULong()
		val expected = Base64.encodeToString(ByteArray(8) { i ->
			((accountId shr (8 * i)) and 0xffUL).toByte()
		}, Base64.NO_WRAP)
		require(expected == rid) { "PSN profile account mismatch" }
		val onlineId = info.getString("onlineId")
		require(onlineId.isNotBlank())
		try {
			val service = http.request(
				USER_PROFILE_SERVICE_URL,
				"Bearer $accessToken", timeoutMs = 5000, operation = "profile discovery"
			)
			val profile = http.request(PsnProfileEndpoint.profileUrl(service.getString("url"), onlineId),
				"Bearer $accessToken", timeoutMs = 5000, operation = "profile")
			val picture = profile.optJSONObject("personalDetail")?.optString("profilePictureUrl").orEmpty()
			PsnProfile(onlineId, picture.ifBlank { profile.optString("avatarUrl", "") })
		} catch(e: kotlinx.coroutines.CancellationException) { throw e }
		catch(_: Exception) { PsnProfile(onlineId, "") }
	}

}
