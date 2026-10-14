// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.psn

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.metallic.chiaki.lib.Holepunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Persisted PSN session (tokens + account id + duid) in EncryptedSharedPreferences.
 * Never included in settings export.
 */
class PsnTokenStore private constructor(private val prefs: SharedPreferences)
{
	companion object
	{
		private const val PREFS_NAME = "chiaki_psn_tokens"
		private const val KEY_ACCESS = "access_token"
		private const val KEY_REFRESH = "refresh_token"
		private const val KEY_EXPIRY = "expiry_epoch_ms"
		private const val KEY_ACCOUNT_ID = "account_id"
		private const val KEY_DUID = "duid"
		private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

		@Volatile
		private var instance: PsnTokenStore? = null

		fun get(context: Context): PsnTokenStore
		{
			instance?.let { return it }
			synchronized(this)
			{
				instance?.let { return it }
				val appContext = context.applicationContext
				val store = try
				{
					val masterKey = MasterKey.Builder(appContext)
						.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
						.build()
					PsnTokenStore(EncryptedSharedPreferences.create(
						appContext,
						PREFS_NAME,
						masterKey,
						EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
						EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
					))
				}
				catch(e: Exception)
				{
					android.util.Log.e("PsnTokenStore", "Encrypted prefs unavailable, falling back", e)
					@Suppress("DEPRECATION")
					PsnTokenStore(appContext.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE))
				}
				instance = store
				return store
			}
		}
	}

	private val refreshMutex = Mutex()

	val isLoggedIn get() = !prefs.getString(KEY_ACCOUNT_ID, "").isNullOrEmpty()
	val accountId: String? get() = prefs.getString(KEY_ACCOUNT_ID, null)
	val duid: String get() = prefs.getString(KEY_DUID, "") ?: ""

	fun save(tokens: PsnAuth.PsnTokens, accountId: String, duid: String)
	{
		prefs.edit()
			.putString(KEY_ACCESS, tokens.accessToken)
			.putString(KEY_REFRESH, tokens.refreshToken)
			.putLong(KEY_EXPIRY, System.currentTimeMillis() + tokens.expiresInSeconds * 1000L)
			.putString(KEY_ACCOUNT_ID, accountId)
			.putString(KEY_DUID, duid)
			.apply()
	}

	fun logout()
	{
		prefs.edit().clear().apply()
	}

	fun accountIdBytes(): ByteArray? =
		accountId?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }

	/** Returns a valid access token, refreshing when it expires within [REFRESH_MARGIN_MS]. */
	suspend fun ensureFreshToken(): String? = refreshMutex.withLock {
		val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
		val access = prefs.getString(KEY_ACCESS, null) ?: return null
		val expiry = prefs.getLong(KEY_EXPIRY, 0L)
		if(expiry - System.currentTimeMillis() > REFRESH_MARGIN_MS)
			return access
		try
		{
			val tokens = PsnAuth.refresh(refresh)
			prefs.edit()
				.putString(KEY_ACCESS, tokens.accessToken)
				.putString(KEY_REFRESH, tokens.refreshToken)
				.putLong(KEY_EXPIRY, System.currentTimeMillis() + tokens.expiresInSeconds * 1000L)
				.apply()
			tokens.accessToken
		}
		catch(e: Exception)
		{
			android.util.Log.e("PsnTokenStore", "Token refresh failed", e)
			null
		}
	}

	/** Generates a fresh duid, logs in with the given code, returns the account id. */
	suspend fun loginWithCode(code: String, onStatus: (PsnLoginPhase) -> Unit = {}): String = withContext(Dispatchers.IO) {
		onStatus(PsnLoginPhase.GENERATING_UID)
		val duid = Holepunch.generateClientDeviceUid()
		if(duid.isEmpty())
			throw PsnLoginException(PsnLoginPhase.GENERATING_UID)
		onStatus(PsnLoginPhase.EXCHANGING)
		val tokens = PsnAuth.exchangeCode(code)
		onStatus(PsnLoginPhase.FETCHING_ACCOUNT)
		val accountId = PsnAuth.fetchAccountId(tokens.accessToken)
		onStatus(PsnLoginPhase.SAVING)
		save(tokens, accountId, duid)
		android.util.Log.i("PsnTokenStore", "PSN login ok, account_id=$accountId")
		accountId
	}
}

enum class PsnLoginPhase { GENERATING_UID, EXCHANGING, FETCHING_ACCOUNT, SAVING }

class PsnLoginException(val phase: PsnLoginPhase) : Exception("PSN login failed at $phase")
