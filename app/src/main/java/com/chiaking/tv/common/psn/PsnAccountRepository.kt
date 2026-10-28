package com.chiaking.tv.common.psn

import android.content.Context
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
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
        const val MAX_ACCOUNTS = PsnTokenStore.MAX_ACCOUNTS
        private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
        @Volatile private var instance: PsnAccountRepository? = null
        fun get(context: Context): PsnAccountRepository = instance ?: synchronized(this) {
            instance ?: PsnAccountRepository(PsnTokenStore.get(context)).also { instance = it }
        }
    }

    private val refreshMutex = Mutex()
    @Volatile private var connectionUsers = 0
    private val _state = MutableStateFlow(snapshot())
    val state = _state.asStateFlow()
    val accountId get() = store.accountId
    val isLoggedIn get() = store.isLoggedIn
    fun accountIdBytes(rid: String? = accountId) = rid?.let(::decodePsnAccountId)
    fun accounts() = store.accounts().map { it.summary() }
    fun account(rid: String) = store.account(rid)?.summary()
    @Synchronized
    fun removeAccount(rid: String) { requireEditable(); store.removeAccount(rid); publish() }
    @Synchronized
    fun selectAccount(rid: String) { requireEditable(); store.selectAccount(rid); publish() }
    private fun requireEditable() {
        if(connectionUsers > 0) throw PsnAccountException(PsnAccountError.CONNECTION_IN_USE)
    }
    @Synchronized
    fun acquireConnectionUsage(): PsnConnectionUsage {
        connectionUsers++
        publish()
        return PsnConnectionUsage(store.accountId) { releaseConnectionUsage() }
    }
    @Synchronized
    private fun releaseConnectionUsage() { connectionUsers--; publish() }

    private fun snapshot() = synchronized(store) {
        PsnAccountsState(store.accountId, store.accounts().map { it.summary() }, connectionUsers > 0)
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
            val saved = store.refreshIfCurrent(rid, account.refreshToken, tokens)
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
	suspend fun loginWithCode(code: String, deviceUid: String? = null, onStatus: (PsnLoginPhase) -> Unit = {}): String = withContext(Dispatchers.IO) {
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
        synchronized(this@PsnAccountRepository) {
            requireEditable()
            store.put(PsnAccount(accountId, tokens.accessToken, tokens.refreshToken, expiry, deviceUid = duid))
        }
		publish()
		// Optional metadata failure does not undo a successful login.
		refreshProfile(accountId, force = true)
		accountId
	}
}

/** Holds account selection steady across asynchronous connection setup and the lifetime of a stream. */
class PsnConnectionUsage internal constructor(val accountId: String?, private val release: () -> Unit) : Closeable {
    private val closed = AtomicBoolean()
    override fun close() { if(closed.compareAndSet(false, true)) release() }
}