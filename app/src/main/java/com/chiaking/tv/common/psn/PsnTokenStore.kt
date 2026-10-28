// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.psn

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** A stored PSN account. [rid] is the base64 account id (LE-encoded userinfo `user_id`). */
data class PsnAccount(
	val rid: String,
	val accessToken: String,
	val refreshToken: String,
	val expiryMs: Long,
	val onlineId: String = "",
	val avatarUrl: String = "",
	val profileUpdatedMs: Long = 0,
    val deviceUid: String = "",
)

/**
 * Persisted PSN session (tokens + account id + duid) in EncryptedSharedPreferences.
 * Never included in settings export.
 */
class PsnTokenStore internal constructor(private val prefs: SharedPreferences)
{
	companion object
	{
		const val MAX_ACCOUNTS = 8
        private const val INDEX = "accounts_v2"
        private const val SELECTED = "connection_account_v2"
        private const val MIGRATED = "accounts_migration"
		private const val PREFS_NAME = "chiaki_psn_tokens"
		private const val KEY_ACCESS = "access_token"
		private const val KEY_REFRESH = "refresh_token"
		private const val KEY_EXPIRY = "expiry_epoch_ms"
		private const val KEY_ACCOUNT_ID = "account_id"
		private const val KEY_DUID = "duid"
		private const val KEY_EXTRA_INDEX = "extra_rids"

		@Volatile
		private var instance: PsnTokenStore? = null

		fun get(context: Context): PsnTokenStore
		{
			instance?.let { return it }
			synchronized(this)
			{
				instance?.let { return it }
				val appContext = context.applicationContext
				val storage = try
				{
					val masterKey = MasterKey.Builder(appContext)
						.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
						.build()
					EncryptedSharedPreferences.create(
						appContext,
						PREFS_NAME,
						masterKey,
						EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
						EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
					)
				}
				catch(e: Exception)
				{
					android.util.Log.e("PsnTokenStore", "Encrypted prefs unavailable, falling back", e)
					@Suppress("DEPRECATION")
					appContext.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE)
				}
				val store = PsnTokenStore(storage)
				instance = store
				return store
			}
		}
	}



    private fun key(rid: String, field: String) = "account_${rid}_$field"
    private val fields = listOf("access", "refresh", "expiry", "online_id", "avatar_url", "profile_updated", "duid")
    init { migrate() }

    private fun index() = prefs.getStringSet(INDEX, emptySet())?.toSet().orEmpty()
    val accountId: String? get() = prefs.getString(SELECTED, null)?.takeIf { account(it) != null }
    val isLoggedIn get() = accountId != null
    val duid get() = accountId?.let { account(it)?.deviceUid }.orEmpty()

    /** Copy legacy credentials/profile metadata and remove old keys in one synchronous atomic edit. */
    @Synchronized
    private fun migrate() {
        if(prefs.getString(MIGRATED, null) == "2") return
        val primary = prefs.getString(KEY_ACCOUNT_ID, null)
        val extras = prefs.getStringSet(KEY_EXTRA_INDEX, emptySet())?.toSet().orEmpty()
        val ids = extras + listOfNotNull(primary)
        val migrated = mutableSetOf<String>()
        val edit = prefs.edit()
        for(rid in ids) {
            val isPrimary = rid == primary
            val access = prefs.getString(if(isPrimary) KEY_ACCESS else "extra_${rid}_access", null)
            val refresh = prefs.getString(if(isPrimary) KEY_REFRESH else "extra_${rid}_refresh", null)
            if(access != null && refresh != null) {
                val account = PsnAccount(rid, access, refresh,
                    prefs.getLong(if(isPrimary) KEY_EXPIRY else "extra_${rid}_expiry", 0),
                    prefs.getString("extra_${rid}_online_id", "").orEmpty(),
                    prefs.getString("extra_${rid}_avatar_url", "").orEmpty(),
                    prefs.getLong("extra_${rid}_profile_updated", 0),
                    if(isPrimary) prefs.getString(KEY_DUID, "").orEmpty() else "")
                write(edit, account)
                migrated.add(rid)
            }
            fields.forEach { edit.remove("extra_${rid}_$it") }
        }
        edit.putStringSet(INDEX, migrated)
            .putString(SELECTED, primary?.takeIf { it in migrated } ?: migrated.sorted().firstOrNull())
            .putString(MIGRATED, "2")
            .remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRY).remove(KEY_ACCOUNT_ID)
            .remove(KEY_DUID).remove(KEY_EXTRA_INDEX)
        check(edit.commit()) { "Unable to migrate PSN accounts" }
    }

    private fun write(edit: SharedPreferences.Editor, account: PsnAccount) {
        edit.putString(key(account.rid, "access"), account.accessToken)
            .putString(key(account.rid, "refresh"), account.refreshToken)
            .putLong(key(account.rid, "expiry"), account.expiryMs)
            .putString(key(account.rid, "online_id"), account.onlineId)
            .putString(key(account.rid, "avatar_url"), account.avatarUrl)
            .putLong(key(account.rid, "profile_updated"), account.profileUpdatedMs)
            .putString(key(account.rid, "duid"), account.deviceUid)
    }

    @Synchronized
    fun accounts(): List<PsnAccount> = index().sorted().mapNotNull { account(it) }

    @Synchronized
    fun account(rid: String): PsnAccount? {
        if(rid !in index()) return null
        val access = prefs.getString(key(rid, "access"), null) ?: return null
        val refresh = prefs.getString(key(rid, "refresh"), null) ?: return null
        return PsnAccount(rid, access, refresh, prefs.getLong(key(rid, "expiry"), 0),
            prefs.getString(key(rid, "online_id"), "").orEmpty(), prefs.getString(key(rid, "avatar_url"), "").orEmpty(),
            prefs.getLong(key(rid, "profile_updated"), 0), prefs.getString(key(rid, "duid"), "").orEmpty())
    }

    @Synchronized
    internal fun put(incoming: PsnAccount) {
        val existing = account(incoming.rid)
        val ids = accounts().map { it.rid }.toSet()
        if(existing == null && ids.size >= MAX_ACCOUNTS) throw PsnAccountException(PsnAccountError.LIMIT_REACHED)
        val preserved = incoming.copy(onlineId = incoming.onlineId.ifBlank { existing?.onlineId.orEmpty() },
            avatarUrl = incoming.avatarUrl.ifBlank { existing?.avatarUrl.orEmpty() },
            profileUpdatedMs = maxOf(incoming.profileUpdatedMs, existing?.profileUpdatedMs ?: 0),
            deviceUid = incoming.deviceUid.ifBlank { existing?.deviceUid.orEmpty() })
        val edit = prefs.edit()
        write(edit, preserved)
        edit.putStringSet(INDEX, ids + incoming.rid)
        if(accountId == null) edit.putString(SELECTED, incoming.rid)
        edit.apply()
    }

    @Synchronized
    fun selectAccount(rid: String) {
        require(account(rid) != null) { "Unknown PSN account" }
        prefs.edit().putString(SELECTED, rid).apply()
    }

    @Synchronized
    fun removeAccount(rid: String) {
        val ids = index() - rid
        val edit = prefs.edit().putStringSet(INDEX, ids)
        fields.forEach { edit.remove(key(rid, it)) }
        if(accountId == rid) edit.putString(SELECTED, ids.sorted().firstOrNull { account(it) != null })
        edit.apply()
    }

    @Synchronized
    internal fun saveProfileIfPresent(rid: String, profile: PsnProfile): Boolean {
        if(account(rid) == null) return false
        prefs.edit().putString(key(rid, "online_id"), profile.onlineId)
            .putString(key(rid, "avatar_url"), profile.avatarUrl)
            .putLong(key(rid, "profile_updated"), System.currentTimeMillis()).apply()
        return true
    }

    @Synchronized
    internal fun refreshIfCurrent(rid: String, previousRefreshToken: String, tokens: PsnTokens): Boolean {
        if(account(rid)?.refreshToken != previousRefreshToken) return false
        prefs.edit().putString(key(rid, "access"), tokens.accessToken).putString(key(rid, "refresh"), tokens.refreshToken)
            .putLong(key(rid, "expiry"), System.currentTimeMillis() + tokens.expiresInSeconds * 1000L).apply()
        return true
    }
}

internal enum class PsnAccountError { LIMIT_REACHED, CONNECTION_IN_USE }
internal class PsnAccountException(val reason: PsnAccountError) : IllegalStateException(reason.name)
