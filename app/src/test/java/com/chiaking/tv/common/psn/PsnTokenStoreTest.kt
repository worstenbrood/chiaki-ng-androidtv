package com.chiaking.tv.common.psn

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PsnTokenStoreTest {
    @Test fun primaryProfileSurvivesReloadAndLateResultsCannotRestoreLoggedOutAccount() {
        val memory = MemoryPreferences()
        val store = PsnTokenStore(memory.prefs)
        primary(store)
        store.putExtra(extra("other"))
        val profile = PsnAuth.Profile("Main Player", "https://example.test/main.png")
        assertTrue(store.saveProfileIfPresent("primary", profile))
        val reloaded = PsnTokenStore(memory.prefs)
        assertEquals("Main Player", reloaded.account("primary")?.onlineId)
        assertEquals(profile.avatarUrl, reloaded.account("primary")?.avatarUrl)
        reloaded.logout()
        assertFalse(reloaded.saveProfileIfPresent("primary", profile))
        assertFalse(memory.values.containsKey("extra_primary_online_id"))
        assertEquals(extra("other"), reloaded.account("other"))
    }

    @Test fun promotingExtraPreservesItsProfileAndSwitchingPrimaryClearsPreviousProfile() {
        val memory = MemoryPreferences()
        val store = PsnTokenStore(memory.prefs)
        primary(store)
        store.saveProfileIfPresent("primary", PsnAuth.Profile("Previous", ""))
        store.putExtra(extra("b"))
        store.saveProfileIfPresent("b", PsnAuth.Profile("Player B", "https://example.test/b.png"))
        primary(store, "b")
        assertEquals(listOf("b"), store.accounts().map { it.rid })
        assertEquals("Player B", store.account("b")?.onlineId)
        assertFalse(memory.values.containsKey("extra_primary_online_id"))
        assertFalse(store.saveProfileIfPresent("primary", PsnAuth.Profile("Late", "")))
    }

    @Test fun profilesPersistWithTheirAccountAndCannotResurrectADeletedAccount() {
        val memory = MemoryPreferences()
        val store = PsnTokenStore(memory.prefs)
        store.putExtra(extra("a"))
        store.putExtra(extra("b"))
        val profile = PsnAuth.Profile("Player B", "https://example.test/avatar.png")
        assertTrue(store.saveProfileIfPresent("b", profile))
        val cached = PsnTokenStore(memory.prefs).account("b")!!
        assertEquals("Player B", cached.onlineId)
        assertEquals(profile.avatarUrl, cached.avatarUrl)
        assertEquals("access-b", cached.accessToken)
        assertTrue(cached.profileUpdatedMs > 0)
        assertTrue(store.refreshExtraIfPresent(extra("b").copy(accessToken = "fresh"), "refresh-b"))
        assertEquals("Player B", store.account("b")?.onlineId)
        assertEquals("fresh", store.account("b")?.accessToken)
        store.removeAccount("b")
        assertFalse(store.saveProfileIfPresent("b", profile))
        assertFalse(memory.values.keys.any { it.startsWith("extra_b_") })
        assertEquals(extra("a"), store.account("a"))
    }
    private fun extra(id: String) = PsnAccount(id, "access-$id", "refresh-$id", 1234)
    private fun primary(store: PsnTokenStore, id: String = "primary") =
        store.save(PsnAuth.PsnTokens("main-access", "main-refresh", 3600), id, "duid")

    @Test fun removingMiddleAccountKeepsOtherCredentialsAndSurvivesReload() {
        val memory = MemoryPreferences()
        val store = PsnTokenStore(memory.prefs)
        primary(store)
        listOf("a", "b", "c").forEach { store.putExtra(extra(it)) }
        store.removeAccount("b")
        val reloaded = PsnTokenStore(memory.prefs)
        assertEquals(listOf("primary", "a", "c"), reloaded.accounts().map { it.rid })
        assertEquals(extra("c"), reloaded.account("c"))
        assertEquals(setOf("a", "c"), memory.values["extra_rids"])
        assertFalse(memory.values.keys.any { it.startsWith("extra_b_") })
        reloaded.putExtra(extra("d"))
        reloaded.putExtra(extra("b"))
        assertEquals(listOf("primary", "a", "b", "c", "d"), reloaded.accounts().map { it.rid })
    }

    @Test fun sevenExtrasAreAllowedInAdditionToPrimaryAndDeletionFreesCapacity() {
        val store = PsnTokenStore(MemoryPreferences().prefs)
        primary(store)
        (1..7).forEach { store.putExtra(extra("$it")) }
        assertEquals(8, store.accounts().size)
        assertEquals(PsnAccountError.LIMIT_REACHED,
            assertThrows(PsnAccountException::class.java) { store.putExtra(extra("8")) }.reason)
        store.putExtra(extra("7").copy(accessToken = "updated"))
        assertEquals(8, store.accounts().size)
        store.removeAccount("4")
        store.putExtra(extra("8"))
        assertEquals(8, store.accounts().size)
        assertEquals("updated", store.account("7")?.accessToken)
    }

    @Test fun removingPrimaryPreservesExtrasAndNewLoginDoesNotDuplicateAnExtra() {
        val store = PsnTokenStore(MemoryPreferences().prefs)
        primary(store)
        store.putExtra(extra("a"))
        store.putExtra(extra("b"))
        store.removeAccount("primary")
        assertFalse(store.isLoggedIn)
        assertEquals(listOf("a", "b"), store.accounts().map { it.rid })
        primary(store, "b")
        assertEquals(listOf("b", "a"), store.accounts().map { it.rid })
        store.logout()
        assertEquals(listOf("a"), store.accounts().map { it.rid })
    }

    @Test fun primaryCannotBeAddedAsExtra() {
        val store = PsnTokenStore(MemoryPreferences().prefs)
        primary(store)
        assertEquals(PsnAccountError.PRIMARY_ACCOUNT,
            assertThrows(PsnAccountException::class.java) { store.putExtra(extra("primary")) }.reason)
        assertEquals(1, store.accounts().size)
    }

    @Test(timeout = 10000) fun primaryTokenLookupDoesNotLockTheRefreshMutexTwice() = runBlocking {
        val store = PsnTokenStore(MemoryPreferences().prefs)
        primary(store)
        assertEquals("main-access", store.ensureFreshTokenFor("primary"))
    }

    @Test fun refreshCannotResurrectDeletedAccountOrOverwriteNewLogin() {
        val store = PsnTokenStore(MemoryPreferences().prefs)
        store.putExtra(extra("a"))
        store.removeAccount("a")
        assertFalse(store.refreshExtraIfPresent(extra("a"), "refresh-a"))
        assertTrue(store.accounts().isEmpty())
        val fresh = extra("a").copy(refreshToken = "new-login")
        store.putExtra(fresh)
        assertFalse(store.refreshExtraIfPresent(extra("a"), "refresh-a"))
        assertEquals(fresh, store.account("a"))
        assertTrue(store.refreshExtraIfPresent(fresh.copy(accessToken = "refreshed"), "new-login"))
    }

    @Test fun legacyRidIndexIsReadWithoutRenumberingOrMutatingSharedSet() {
        val memory = MemoryPreferences()
        val legacyIndex = setOf("c", "a", "b")
        memory.values["extra_rids"] = legacyIndex
        legacyIndex.forEach {
            memory.values["extra_${it}_access"] = "access-$it"
            memory.values["extra_${it}_refresh"] = "refresh-$it"
            memory.values["extra_${it}_expiry"] = 1234L
        }
        val store = PsnTokenStore(memory.prefs)
        store.removeAccount("b")
        assertEquals(setOf("a", "b", "c"), legacyIndex)
        assertEquals(listOf(extra("a"), extra("c")), store.accounts())
        store.removeAccount("missing")
        assertEquals(listOf(extra("a"), extra("c")), store.accounts())
    }

    @Test fun stalePrimaryRefreshCannotRestoreRemovedCredentialsOrOverwriteAnotherAccount() {
        val memory = MemoryPreferences()
        val store = PsnTokenStore(memory.prefs)
        primary(store)
        val tokens = PsnAuth.PsnTokens("stale-access", "stale-refresh", 3600)
        store.logout()
        assertFalse(store.refreshPrimaryIfCurrent("primary", "main-refresh", tokens))
        assertFalse(memory.values.containsKey("access_token"))
        primary(store, "different")
        assertFalse(store.refreshPrimaryIfCurrent("primary", "main-refresh", tokens))
        assertEquals("main-access", store.account("different")?.accessToken)
    }

    /** Test the SharedPreferences contract without an Android device or encrypted test secrets. */
    private class MemoryPreferences {
        val values = mutableMapOf<String, Any?>()
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when(method.name) {
                "edit" -> editor()
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0] as String)
                "getString", "getLong", "getStringSet" -> values[args!![0] as String] ?: args[1]
                else -> error("Unexpected SharedPreferences method ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val changes = mutableMapOf<String, Any?>()
            lateinit var proxy: SharedPreferences.Editor
            proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                when(method.name) {
                    "putString", "putLong", "putStringSet" -> {
                        changes[args!![0] as String] = args[1]
                        proxy
                    }
                    "remove" -> { changes[args!![0] as String] = null; proxy }
                    "apply", "commit" -> {
                        changes.forEach { (key, value) ->
                            if(value == null) values.remove(key) else values[key] = value
                        }
                        if(method.name == "commit") true else null
                    }
                    else -> error("Unexpected Editor method ${method.name}")
                }
            } as SharedPreferences.Editor
            return proxy
        }
    }
}
