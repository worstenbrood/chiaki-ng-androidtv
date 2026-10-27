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
class PsnTokenStore internal constructor(private val prefs: SharedPreferences)
{
	companion object
	{
		const val MAX_EXTRA_ACCOUNTS = 7
		private const val PREFS_NAME = "chiaki_psn_tokens"
		private const val KEY_ACCESS = "access_token"
		private const val KEY_REFRESH = "refresh_token"
		private const val KEY_EXPIRY = "expiry_epoch_ms"
		private const val KEY_ACCOUNT_ID = "account_id"
		private const val KEY_DUID = "duid"
		private const val KEY_EXTRA_INDEX = "extra_rids"
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

	@Synchronized
	fun save(tokens: PsnAuth.PsnTokens, accountId: String, duid: String)
	{
		prefs.edit()
			.putString(KEY_ACCESS, tokens.accessToken)
			.putString(KEY_REFRESH, tokens.refreshToken)
			.putLong(KEY_EXPIRY, System.currentTimeMillis() + tokens.expiresInSeconds * 1000L)
			.putString(KEY_ACCOUNT_ID, accountId)
			.putString(KEY_DUID, duid)
			.remove(extraKey(accountId, "access"))
			.remove(extraKey(accountId, "refresh"))
			.remove(extraKey(accountId, "expiry"))
			.remove(extraKey(accountId, "online_id"))
			.remove(extraKey(accountId, "avatar_url"))
			.remove(extraKey(accountId, "profile_updated"))
			.putStringSet(KEY_EXTRA_INDEX, extraRids() - accountId)
			.apply()
	}

	@Synchronized
	fun logout()
	{
		prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRY)
			.remove(KEY_ACCOUNT_ID).remove(KEY_DUID).apply()
	}

	fun accountIdBytes(): ByteArray? =
		accountId?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }

	private fun extraKey(rid: String, field: String) = "extra_${rid}_$field"

	private fun extraRids(): Set<String> = prefs.getStringSet(KEY_EXTRA_INDEX, emptySet())?.toSet() ?: emptySet()

	private fun loadExtra(rid: String): PsnAccount?
	{
		val access = prefs.getString(extraKey(rid, "access"), null) ?: return null
		val refresh = prefs.getString(extraKey(rid, "refresh"), null) ?: return null
		return PsnAccount(rid, access, refresh, prefs.getLong(extraKey(rid, "expiry"), 0L))
	}

	private fun loadExtras(): List<PsnAccount> = extraRids().sorted().filter { it != accountId }.mapNotNull { loadExtra(it) }

	@Synchronized
	internal fun putExtra(account: PsnAccount)
	{
		if(account.rid == accountId) throw PsnAccountException(PsnAccountError.PRIMARY_ACCOUNT)
		val index = loadExtras().map { it.rid }.toSet()
		if(account.rid !in index && index.size >= MAX_EXTRA_ACCOUNTS)
			throw PsnAccountException(PsnAccountError.LIMIT_REACHED)
		prefs.edit()
			.putString(extraKey(account.rid, "access"), account.accessToken)
			.putString(extraKey(account.rid, "refresh"), account.refreshToken)
			.putLong(extraKey(account.rid, "expiry"), account.expiryMs)
			.putStringSet(KEY_EXTRA_INDEX, index + account.rid)
			.apply()
	}

	private fun deleteExtra(rid: String)
	{
		prefs.edit()
			.remove(extraKey(rid, "access"))
			.remove(extraKey(rid, "refresh"))
			.remove(extraKey(rid, "expiry"))
			.remove(extraKey(rid, "online_id"))
			.remove(extraKey(rid, "avatar_url"))
			.remove(extraKey(rid, "profile_updated"))
			.putStringSet(KEY_EXTRA_INDEX, extraRids() - rid)
			.apply()
	}

	/** All accounts: primary first (if logged in), then co-op extras. */
	@Synchronized
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

	@Synchronized
	fun removeAccount(rid: String)
	{
		if(rid == accountId) { logout(); return }
		deleteExtra(rid)
	}

	@Synchronized
	internal fun refreshExtraIfPresent(account: PsnAccount, previousRefreshToken: String): Boolean {
		if(loadExtra(account.rid)?.refreshToken != previousRefreshToken || account.rid !in extraRids()) return false
		// Update only the account whose credentials are still current.
		prefs.edit().putString(extraKey(account.rid, "access"), account.accessToken)
			.putString(extraKey(account.rid, "refresh"), account.refreshToken)
			.putLong(extraKey(account.rid, "expiry"), account.expiryMs).apply()
		return true
	}

	/** Returns a valid access token for [rid], refreshing when near expiry. */
	suspend fun ensureFreshTokenFor(rid: String): String? {
		if(rid == accountId) return ensureFreshToken()
		return refreshMutex.withLock {
			val acct = loadExtra(rid) ?: return null
			if(acct.expiryMs - System.currentTimeMillis() > REFRESH_MARGIN_MS)
				return acct.accessToken
			try
			{
				val tokens = PsnAuth.refresh(acct.refreshToken)
				val updated = acct.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken,
					expiryMs = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L)
				if(refreshExtraIfPresent(updated, acct.refreshToken)) updated.accessToken else null
			}
			catch(e: Exception)
			{
				android.util.Log.e("PsnTokenStore", "Token refresh failed for $rid", e)
				null
			}
		}
	}

	/** Returns a valid access token, refreshing when it expires within [REFRESH_MARGIN_MS]. */
	suspend fun ensureFreshToken(): String? = refreshMutex.withLock {
		val rid = accountId ?: return null
		val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
		val access = prefs.getString(KEY_ACCESS, null) ?: return null
		val expiry = prefs.getLong(KEY_EXPIRY, 0L)
		if(expiry - System.currentTimeMillis() > REFRESH_MARGIN_MS)
			return access
		try
		{
			val tokens = PsnAuth.refresh(refresh)
			if(refreshPrimaryIfCurrent(rid, refresh, tokens)) tokens.accessToken else null
		}
		catch(e: Exception)
		{
			android.util.Log.e("PsnTokenStore", "Token refresh failed", e)
			null
		}
	}

	@Synchronized
	internal fun refreshPrimaryIfCurrent(rid: String, previousRefreshToken: String, tokens: PsnAuth.PsnTokens): Boolean {
		if(accountId != rid || prefs.getString(KEY_REFRESH, null) != previousRefreshToken) return false
		prefs.edit().putString(KEY_ACCESS, tokens.accessToken).putString(KEY_REFRESH, tokens.refreshToken)
			.putLong(KEY_EXPIRY, System.currentTimeMillis() + tokens.expiresInSeconds * 1000L).apply()
		return true
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
		if(asExtra)
			putExtra(PsnAccount(accountId, tokens.accessToken, tokens.refreshToken, expiry))
		else
			save(tokens, accountId, duid)
		android.util.Log.i("PsnTokenStore", "PSN login ok, account_id=$accountId extra=$asExtra")
		accountId
	}
}

internal enum class PsnAccountError { LIMIT_REACHED, PRIMARY_ACCOUNT }
internal class PsnAccountException(val reason: PsnAccountError) : IllegalStateException(reason.name)
