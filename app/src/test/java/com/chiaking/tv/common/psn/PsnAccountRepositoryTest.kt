package com.chiaking.tv.common.psn

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PsnAccountRepositoryTest {
    @Test(timeout = 10000) fun removingAnAccountDuringTokenRefreshCannotRestoreCredentials() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.putExtra(PsnAccount("account", "expired", "refresh", 0))
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

    @Test(timeout = 10000) fun profileFailureDoesNotUndoSuccessfulExtraLogin() = runBlocking {
        val repository = PsnAccountRepository(PsnTokenStore(MemoryPsnPreferences().prefs), auth,
            object : PsnProfileService {
                override suspend fun fetchProfile(rid: String, accessToken: String): PsnProfile =
                    throw java.io.IOException("offline")
            }, { "uid" })
        repository.loginWithCode("code", asExtra = true)
        assertFalse(repository.isLoggedIn)
        assertEquals(listOf(PsnAccountSummary("account", "", "")), repository.state.value.accounts)
    }

    @Test(timeout = 10000) fun lateProfileCannotRestoreAnAccountRemovedDuringFetch() = runBlocking {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.save(PsnTokens("access", "refresh", 3600), "account", "uid")
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
        repository.logout()
        response.complete(PsnProfile("Late", "https://example.test/late.png"))
        assertNull(request.await())
        assertEquals(PsnAccountsState(null, emptyList()), repository.state.value)
    }
}
