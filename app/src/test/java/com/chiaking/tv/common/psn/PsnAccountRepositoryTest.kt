package com.chiaking.tv.common.psn

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PsnAccountRepositoryTest {
    @Test fun connectionLeasesBlockSelectionAndRemovalUntilLastLeaseCloses() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(PsnAccount("a", "a", "a", Long.MAX_VALUE))
        store.put(PsnAccount("b", "b", "b", Long.MAX_VALUE))
        val repository = PsnAccountRepository(store, auth)
        val connection = repository.acquireConnectionUsage()
        val stream = repository.acquireConnectionUsage()
        assertEquals("a", connection.accountId)
        assertTrue(repository.state.value.connectionInUse)
        try { repository.selectAccount("b"); fail("Expected active connection") }
        catch(e: PsnAccountException) { assertEquals(PsnAccountError.CONNECTION_IN_USE, e.reason) }
        connection.close(); connection.close()
        try { repository.removeAccount("a"); fail("Expected active stream") }
        catch(e: PsnAccountException) { assertEquals(PsnAccountError.CONNECTION_IN_USE, e.reason) }
        stream.close()
        assertFalse(repository.state.value.connectionInUse)
        repository.selectAccount("b")
        assertEquals("b", repository.state.value.connectionAccountId)
        repository.removeAccount("b")
        assertEquals("a", repository.state.value.connectionAccountId)
    }

    @Test(timeout = 10000) fun accountLoginCannotMutateCredentialsWhileAStreamIsActive() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(PsnAccount("a", "access", "refresh", Long.MAX_VALUE))
        val repository = PsnAccountRepository(store, auth)
        val stream = repository.acquireConnectionUsage()
        try {
            repository.loginWithCode("code", deviceUid = "uid")
            fail("Expected account mutation to be blocked")
        } catch(e: PsnAccountException) { assertEquals(PsnAccountError.CONNECTION_IN_USE, e.reason) }
        finally { stream.close() }
        assertEquals(listOf("a"), repository.accounts().map { it.rid })
    }

    @Test(timeout = 10000) fun addingAnotherAccountPreservesCurrentDefault() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(PsnAccount("a", "access", "refresh", Long.MAX_VALUE))
        val repository = PsnAccountRepository(store, auth, object : PsnProfileService {
            override suspend fun fetchProfile(rid: String, accessToken: String) = PsnProfile("Player", "")
        })
        repository.loginWithCode("code", deviceUid = "uid")
        assertEquals("a", repository.accountId)
        assertEquals(setOf("a", "account"), repository.accounts().map { it.rid }.toSet())
    }
    @Test(timeout = 10000) fun loginPreservesDeviceIdentityFromAuthorizationAttempt() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        val repository = PsnAccountRepository(store, auth, object : PsnProfileService {
            override suspend fun fetchProfile(rid: String, accessToken: String) = PsnProfile("Player", "")
        }, { throw AssertionError("Must reuse the login attempt device ID") })
        repository.loginWithCode("code", deviceUid = "authorized-device")
        assertEquals("authorized-device", store.duid)
    }
    @Test(timeout = 10000) fun removingAnAccountDuringTokenRefreshCannotRestoreCredentials() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(PsnAccount("account", "expired", "refresh", 0))
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<PsnTokens>()
        val controlledAuth = object : PsnAuthentication by auth {
            override suspend fun refresh(refreshToken: String): PsnTokens {
                started.complete(Unit)
                return response.await()
            }
        }
        val repository = PsnAccountRepository(store, controlledAuth)
        val request = async { repository.ensureFreshTokenFor("account") }
        started.await()
        repository.removeAccount("account")
        response.complete(PsnTokens("late", "late-refresh", 3600))
        assertNull(request.await())
        assertTrue(store.accounts().isEmpty())
        assertTrue(repository.state.value.accounts.isEmpty())
    }

    private val auth = object : PsnAuthentication {
        override suspend fun exchangeCode(code: String) = PsnTokens("access", "refresh", 3600)
        override suspend fun refresh(refreshToken: String) = PsnTokens("fresh", "rotated", 3600)
        override suspend fun fetchAccountId(accessToken: String) = "account"
    }

    @Test(timeout = 10000) fun loginFetchesProfileAndPublishesSafeAccountState() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        val repository = PsnAccountRepository(store, auth, object : PsnProfileService {
            override suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile {
                assertEquals("account", rid)
                assertEquals("access", accessToken)
                return PsnProfile("Player", "https://example.test/player.png")
            }
        }, { "uid" })
        repository.loginWithCode("code")
        assertEquals(PsnAccountsState("account", listOf(PsnAccountSummary("account", "Player",
            "https://example.test/player.png"))), repository.state.value)
        assertEquals("access", store.account("account")?.accessToken)
    }

    @Test(timeout = 10000) fun profileFailureDoesNotUndoSuccessfulLogin() = runBlocking {
        val repository = PsnAccountRepository(PsnTokenStore(MemoryPsnPreferences().prefs), auth,
            object : PsnProfileService {
                override suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile =
                    throw java.io.IOException("offline")
            }, { "uid" })
        repository.loginWithCode("code", deviceUid = "uid")
        assertTrue(repository.isLoggedIn)
        assertEquals(listOf(PsnAccountSummary("account", "", "")), repository.state.value.accounts)
    }

    @Test(timeout = 10000) fun lateProfileCannotRestoreAnAccountRemovedDuringFetch() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(PsnAccount("account", "access", "refresh", System.currentTimeMillis() + 3600000, deviceUid = "uid"))
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<PsnProfile>()
        val repository = PsnAccountRepository(store, auth, object : PsnProfileService {
            override suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile {
                started.complete(Unit)
                return response.await()
            }
        })
        val request = async { repository.refreshProfile("account", force = true) }
        started.await()
        repository.removeAccount("account")
        response.complete(PsnProfile("Late", "https://example.test/late.png"))
        assertNull(request.await())
        assertEquals(PsnAccountsState(null, emptyList()), repository.state.value)
    }
}
