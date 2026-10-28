package com.chiaking.tv.common.psn

import android.content.Context
import com.metallic.chiaki.lib.Holepunch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinates authentication, cached profiles and secure storage; UI only receives summaries. */
class PsnAccountRepository internal constructor(
    private val store: PsnTokenStore,
    private val auth: PsnAuthentication = PsnAuth,
    private val profiles: PsnProfileService = PsnProfileClient,
    private val generateUid: () -> String = { Holepunch.generateClientDeviceUid() },
) {
    companion object {
        const val MAX_EXTRA_ACCOUNTS = PsnTokenStore.MAX_EXTRA_ACCOUNTS
        private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
        @Volatile private var instance: PsnAccountRepository? = null
        fun get(context: Context): PsnAccountRepository = instance ?: synchronized(this) {
            instance ?: PsnAccountRepository(PsnTokenStore.get(context)).also { instance = it }
        }
    }

    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow(snapshot())
    val state = _state.asStateFlow()
    val accountId get() = store.accountId
    val isLoggedIn get() = store.isLoggedIn
    fun accountIdBytes() = store.accountIdBytes()
    fun accounts() = store.accounts().map { it.summary() }
    fun account(rid: String) = store.account(rid)?.summary()
    fun logout() { store.logout(); publish() }
    fun removeAccount(rid: String) { store.removeAccount(rid); publish() }

    private fun snapshot() = synchronized(store) {
        PsnAccountsState(store.accountId, store.accounts().map { it.summary() })
    }
    private fun publish() = synchronized(store) { _state.value = snapshot() }

    suspend fun ensureFreshTokenFor(rid: String): String? = refreshMutex.withLock {
        refreshTokenLocked(rid)
    }

    suspend fun ensureFreshToken(): String? = refreshMutex.withLock {
        store.accountId?.let { refreshTokenLocked(it) }
    }

    private suspend fun refreshTokenLocked(rid: String): String? {
        val account = store.account(rid) ?: return null
        if(account.expiryMs - System.currentTimeMillis() > REFRESH_MARGIN_MS) return account.accessToken
        return try {
            val tokens = auth.refresh(account.refreshToken)
            val saved = if(rid == store.accountId)
                store.refreshPrimaryIfCurrent(rid, account.refreshToken, tokens)
            else store.refreshExtraIfPresent(account.copy(accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiryMs = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L), account.refreshToken)
            if(saved) tokens.accessToken else null
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            android.util.Log.e("PsnAccounts", "Token refresh failed", e)
            null
        }
    }

	suspend fun refreshProfile(rid: String, force: Boolean = false): PsnAccountSummary? = withContext(Dispatchers.IO) {
		val cached = store.account(rid) ?: return@withContext null
		if(!force && cached.profileUpdatedMs > System.currentTimeMillis() -
			(if(cached.avatarUrl.isBlank()) 5 * 60 * 1000L else 24 * 60 * 60 * 1000L))
			return@withContext cached.summary()
		try {
			val token = ensureFreshTokenFor(rid) ?: return@withContext cached.summary()
			val profile = profiles.fetchProfile(rid, token)
			store.saveProfileIfPresent(rid, profile)
		} catch(e: kotlinx.coroutines.CancellationException) { throw e }
		catch(_: Exception) { /* Optional profile data must never prevent joining. */ }
		publish()
		store.account(rid)?.summary()
	}

	/** Reuses the authorization attempt's device ID (or generates one for legacy callers). */
	suspend fun loginWithCode(code: String, asExtra: Boolean = false, deviceUid: String? = null, onStatus: (PsnLoginPhase) -> Unit = {}): String = withContext(Dispatchers.IO) {
		onStatus(PsnLoginPhase.GENERATING_UID)
		val duid = deviceUid ?: generateUid()
		if(duid.isEmpty())
			throw PsnLoginException(PsnLoginPhase.GENERATING_UID)
		onStatus(PsnLoginPhase.EXCHANGING)
		val tokens = auth.exchangeCode(code)
		onStatus(PsnLoginPhase.FETCHING_ACCOUNT)
		val accountId = auth.fetchAccountId(tokens.accessToken)
		onStatus(PsnLoginPhase.SAVING)
		val expiry = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L
		if(asExtra)
			store.putExtra(PsnAccount(accountId, tokens.accessToken, tokens.refreshToken, expiry))
		else
			store.save(tokens, accountId, duid)
		publish()
		// Optional metadata failure does not undo a successful login.
		refreshProfile(accountId, force = true)
		accountId
	}
}
