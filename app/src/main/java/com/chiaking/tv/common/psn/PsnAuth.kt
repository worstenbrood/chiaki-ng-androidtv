// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.psn

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * PSN OAuth2 flow, mirroring external/chiaki-ng gui/src/psnaccountid.cpp + psntoken.cpp.
 * The user opens [loginUrl] on a phone, logs in, and pastes back the final
 * remoteplay.dl.playstation.net redirect URL (or just its code parameter).
 */
object PsnAuth
{
	const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
	const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
	const val REDIRECT_PAGE = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
	const val SCOPES = "psn:clientapp referenceDataService:countryConfig.read " +
		"pushNotification:webSocket.desktop.connect sessionManager:remotePlaySession.system.update"

	private const val AUTHORIZE_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize"
	private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"
	private val http = PsnHttpClient()

	data class PsnTokens(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long)
	data class Profile(val onlineId: String, val avatarUrl: String)

	/** Optional profile data for account avatars; failures must not prevent Remote Play. */
	suspend fun fetchProfile(rid: String, accessToken: String): Profile = withContext(Dispatchers.IO) {
		require(Base64.decode(rid, Base64.NO_WRAP).size == 8)
		val service = http.request(
			"https://asm.np.community.playstation.net/asm/v1/apps/me/baseUrls/userProfile",
			"Bearer $accessToken", timeoutMs = 5000, operation = "profile discovery"
		)
		val json = http.request(
			PsnProfileEndpoint.profileUrl(service.getString("url")),
			"Bearer $accessToken", timeoutMs = 5000, operation = "profile"
		)
		Profile(json.optString("onlineId", ""), json.optString("avatarUrl", ""))
	}

	fun loginUrl(duid: String): String =
		"$AUTHORIZE_URL?service_entity=urn:service-entity:psn&response_type=code" +
			"&client_id=$CLIENT_ID&redirect_uri=${enc(REDIRECT_PAGE)}&scope=${enc(SCOPES)}" +
			"&request_locale=en_US&ui=pr&service_logo=ps&layout_type=popup&smcid=remoteplay" +
			"&prompt=always&PlatformPrivacyWs1=minimal&duid=$duid&"

	/** Accepts the full redirect URL or a bare authorization code; returns the code. */
	fun parseRedirect(input: String): String?
	{
		val trimmed = input.trim()
		if(trimmed.startsWith(REDIRECT_PAGE))
		{
			val query = trimmed.substringAfter('?', "")
			return query.split('&')
				.firstOrNull { it.startsWith("code=") }
				?.substringAfter('=')
				?.takeIf { it.isNotEmpty() }
		}
		return trimmed.takeIf { it.matches(Regex("[A-Za-z0-9_\\-.]+")) && it.length >= 4 }
	}

	suspend fun exchangeCode(code: String): PsnTokens = withContext(Dispatchers.IO) {
		request(
			"grant_type=authorization_code&code=$code&scope=${enc(SCOPES)}&redirect_uri=${enc(REDIRECT_PAGE)}"
		)
	}

	suspend fun refresh(refreshToken: String): PsnTokens = withContext(Dispatchers.IO) {
		request(
			"grant_type=refresh_token&refresh_token=$refreshToken&scope=${enc(SCOPES)}&redirect_uri=${enc(REDIRECT_PAGE)}"
		)
	}

	/** Account-ID: user_id as 8-byte little-endian, base64 (see psnaccountid.cpp). */
	suspend fun fetchAccountId(accessToken: String): String = withContext(Dispatchers.IO) {
		val json = httpRequest("GET", "$TOKEN_URL/$accessToken", null)
		val userId = json.getString("user_id").toULong()
		val bytes = ByteArray(8) { i -> ((userId shr (8 * i)) and 0xffUL).toByte() }
		Base64.encodeToString(bytes, Base64.NO_WRAP)
	}

	private suspend fun request(body: String): PsnTokens
	{
		val json = httpRequest("POST", TOKEN_URL, body)
		return PsnTokens(
			json.getString("access_token"),
			json.getString("refresh_token"),
			json.optLong("expires_in", 3600L)
		)
	}

	private fun httpRequest(method: String, url: String, body: String?): JSONObject {
		require(method == if(body == null) "GET" else "POST")
		return http.request(url, basicAuth(), body)
	}
	private fun basicAuth(): String =
		"Basic " + Base64.encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP)

	private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
