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
import org.json.JSONArray
import org.json.JSONObject

/** A stored PSN account. [rid] is the base64 account id (LE-encoded userinfo `user_id`). */
data class PsnAccount(
	val rid: String,
	val accessToken: String,
	val refreshToken: String,
	val expiryMs: Long,
)

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
		private const val KEY_ACCOUNTS = "extra_accounts_v1"
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

	private fun loadExtras(): MutableList<PsnAccount>
	{
		val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return mutableListOf()
		return runCatching {
			val arr = JSONArray(raw)
			(0 until arr.length()).mapNotNull { i ->
				val o = arr.optJSONObject(i) ?: return@mapNotNull null
				val rid = o.optString("rid")
				if(rid.isEmpty()) return@mapNotNull null
				PsnAccount(rid, o.optString("access"), o.optString("refresh"), o.optLong("expiry"))
			}.toMutableList()
		}.getOrDefault(mutableListOf())
	}

	private fun saveExtras(list: List<PsnAccount>)
	{
		val arr = JSONArray()
		for(a in list)
			arr.put(JSONObject().put("rid", a.rid).put("access", a.accessToken)
				.put("refresh", a.refreshToken).put("expiry", a.expiryMs))
		prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
	}

	/** All accounts: primary first (if logged in), then co-op extras. */
	fun accounts(): List<PsnAccount>
	{
		val out = mutableListOf<PsnAccount>()
		val primaryRid = accountId
		if(!primaryRid.isNullOrEmpty())
			out.add(PsnAccount(primaryRid, prefs.getString(KEY_ACCESS, "") ?: "",
				prefs.getString(KEY_REFRESH, "") ?: "", prefs.getLong(KEY_EXPIRY, 0L)))
		out.addAll(loadExtras())
		return out
	}

	fun account(rid: String): PsnAccount? = accounts().firstOrNull { it.rid == rid }

	fun removeAccount(rid: String)
	{
		if(rid == accountId) { logout(); return }
		saveExtras(loadExtras().filterNot { it.rid == rid })
	}

	private fun upsertExtra(account: PsnAccount)
	{
		val list = loadExtras().filterNot { it.rid == account.rid }.toMutableList()
		list.add(account)
		saveExtras(list)
	}

	/** Returns a valid access token for [rid], refreshing when near expiry. */
	suspend fun ensureFreshTokenFor(rid: String): String? = refreshMutex.withLock {
		if(rid == accountId) return@withLock ensureFreshToken()
		val list = loadExtras()
		val acct = list.firstOrNull { it.rid == rid } ?: return null
		if(acct.expiryMs - System.currentTimeMillis() > REFRESH_MARGIN_MS)
			return acct.accessToken
		try
		{
			val tokens = PsnAuth.refresh(acct.refreshToken)
			val updated = acct.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken,
				expiryMs = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L)
			saveExtras(list.map { if(it.rid == rid) updated else it })
			updated.accessToken
		}
		catch(e: Exception)
		{
			android.util.Log.e("PsnTokenStore", "Token refresh failed for $rid", e)
			null
		}
	}

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
	suspend fun loginWithCode(code: String, asExtra: Boolean = false, onStatus: (PsnLoginPhase) -> Unit = {}): String = withContext(Dispatchers.IO) {
		onStatus(PsnLoginPhase.GENERATING_UID)
		val duid = Holepunch.generateClientDeviceUid()
		if(duid.isEmpty())
			throw PsnLoginException(PsnLoginPhase.GENERATING_UID)
		onStatus(PsnLoginPhase.EXCHANGING)
		val tokens = PsnAuth.exchangeCode(code)
		onStatus(PsnLoginPhase.FETCHING_ACCOUNT)
		val accountId = PsnAuth.fetchAccountId(tokens.accessToken)
		onStatus(PsnLoginPhase.SAVING)
		val expiry = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L
		if(asExtra && accountId != prefs.getString(KEY_ACCOUNT_ID, null))
			upsertExtra(PsnAccount(accountId, tokens.accessToken, tokens.refreshToken, expiry))
		else
			save(tokens, accountId, duid)
		android.util.Log.i("PsnTokenStore", "PSN login ok, account_id=$accountId extra=$asExtra")
		accountId
	}
}
