package com.chiaking.tv.common.psn

import org.junit.Assert.*
import org.junit.Test

class PsnTokenStoreTest {
    private fun account(id: String) = PsnAccount(id, "access-$id", "refresh-$id", 1234)
    private fun legacy(): MemoryPsnPreferences = MemoryPsnPreferences().apply {
        values["account_id"] = "main"
        values["access_token"] = "main-access"
        values["refresh_token"] = "main-refresh"
        values["expiry_epoch_ms"] = 5678L
        values["duid"] = "device"
        values["extra_main_online_id"] = "Main Player"
        values["extra_main_avatar_url"] = "https://example.test/main.png"
        values["extra_main_profile_updated"] = 456L
        values["extra_rids"] = (1..7).map { "other-$it" }.toSet()
        for(id in 1..7) {
            values["extra_other-${id}_access"] = "access-$id"
            values["extra_other-${id}_refresh"] = "refresh-$id"
            values["extra_other-${id}_expiry"] = 9000L + id
            values["extra_other-${id}_online_id"] = "Player $id"
            values["extra_other-${id}_avatar_url"] = "https://example.test/$id.png"
        }
    }

    @Test fun migratesPrimaryAndSevenExtrasWithoutLosingCredentialsOrProfiles() {
        val memory = legacy()
        val store = PsnTokenStore(memory.prefs)
        assertEquals("main", store.accountId)
        assertEquals(8, store.accounts().size)
        assertEquals(PsnAccount("main", "main-access", "main-refresh", 5678,
            "Main Player", "https://example.test/main.png", 456, "device"), store.account("main"))
        for(id in 1..7) {
            val account = store.account("other-$id")!!
            assertEquals("access-$id", account.accessToken)
            assertEquals("refresh-$id", account.refreshToken)
            assertEquals("Player $id", account.onlineId)
        }
        assertFalse(memory.values.containsKey("access_token"))
        assertFalse(memory.values.keys.any { it.startsWith("extra_") })
        assertEquals(store.accounts(), PsnTokenStore(memory.prefs).accounts())
    }

    @Test fun migrationIsNotRepeatedAfterRemovingAnAccount() {
        val memory = legacy()
        val store = PsnTokenStore(memory.prefs)
        store.removeAccount("main")
        val reloaded = PsnTokenStore(memory.prefs)
        assertNull(reloaded.account("main"))
        assertEquals("other-1", reloaded.accountId)
        assertEquals(7, reloaded.accounts().size)
    }

    @Test fun failedMigrationKeepsLegacyDataForRetry() {
        val memory = legacy().apply { commitSucceeds = false }
        try { PsnTokenStore(memory.prefs); fail("Expected migration failure") } catch(_: IllegalStateException) { }
        assertEquals("main-access", memory.values["access_token"])
        assertFalse(memory.values.containsKey("accounts_migration"))
        memory.commitSucceeds = true
        assertEquals(8, PsnTokenStore(memory.prefs).accounts().size)
    }

    @Test fun legacyExtrasWithoutMainSelectAValidAccountAndIgnoreBrokenEntries() {
        val memory = MemoryPsnPreferences()
        memory.values["extra_rids"] = setOf("valid", "broken")
        memory.values["extra_valid_access"] = "access"
        memory.values["extra_valid_refresh"] = "refresh"
        val store = PsnTokenStore(memory.prefs)
        assertEquals("valid", store.accountId)
        assertEquals(listOf("valid"), store.accounts().map { it.rid })
    }

    @Test fun removingMiddleAccountNeverRenumbersOrChangesOtherCredentials() {
        val memory = MemoryPsnPreferences()
        val store = PsnTokenStore(memory.prefs)
        listOf("a", "b", "c").forEach { store.put(account(it)) }
        store.selectAccount("c")
        store.removeAccount("b")
        val reloaded = PsnTokenStore(memory.prefs)
        assertEquals(listOf(account("a"), account("c")), reloaded.accounts())
        assertEquals("c", reloaded.accountId)
        assertFalse(memory.values.keys.any { it.startsWith("account_b_") })
    }

    @Test fun selectedAccountIsAReferenceAndSwitchingDoesNotMoveTokens() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(account("a")); store.put(account("b"))
        store.selectAccount("b")
        assertEquals("b", store.accountId)
        assertEquals(account("a"), store.account("a"))
        assertEquals(account("b"), store.account("b"))
        try { store.selectAccount("missing"); fail("Expected unknown account") } catch(_: IllegalArgumentException) { }
        assertEquals("b", store.accountId)
    }

    @Test fun eightAccountLimitAllowsReauthenticationOfExistingAccount() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        (1..8).forEach { store.put(account("$it")) }
        try { store.put(account("9")); fail("Expected limit") }
        catch(e: PsnAccountException) { assertEquals(PsnAccountError.LIMIT_REACHED, e.reason) }
        store.put(account("4").copy(accessToken = "new"))
        assertEquals(8, store.accounts().size)
        assertEquals("1", store.accountId)
        assertEquals("new", store.account("4")?.accessToken)
        store.removeAccount("3")
        store.put(account("9"))
        assertEquals(8, store.accounts().size)
    }

    @Test fun removingDefaultSelectsAnotherAndRemovingLastSignsOut() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(account("a")); store.put(account("b"))
        store.removeAccount("a")
        assertEquals("b", store.accountId)
        store.removeAccount("b")
        assertNull(store.accountId)
        assertFalse(store.isLoggedIn)
    }

    @Test fun refreshForPreviousSelectionUpdatesOnlyThatAccount() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(account("a")); store.put(account("b"))
        store.selectAccount("b")
        assertTrue(store.refreshIfCurrent("a", "refresh-a", PsnTokens("fresh", "rotated", 3600)))
        assertEquals("fresh", store.account("a")?.accessToken)
        assertEquals(account("b"), store.account("b"))
        assertEquals("b", store.accountId)
    }

    @Test fun lateRefreshAndProfileCannotRestoreDeletedAccountOrOverwriteNewLogin() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(account("a")); store.removeAccount("a")
        assertFalse(store.saveProfileIfPresent("a", PsnProfile("Late", "")))
        assertFalse(store.refreshIfCurrent("a", "refresh-a", PsnTokens("late", "late", 3600)))
        store.put(account("a").copy(refreshToken = "new-login"))
        assertFalse(store.refreshIfCurrent("a", "refresh-a", PsnTokens("late", "late", 3600)))
        assertEquals("new-login", store.account("a")?.refreshToken)
    }

    @Test fun reauthenticationAndTokenRefreshPreserveNewerProfileMetadata() {
        val store = PsnTokenStore(MemoryPsnPreferences().prefs)
        store.put(account("a"))
        store.saveProfileIfPresent("a", PsnProfile("Player", "https://example.test/a.png"))
        store.put(account("a").copy(refreshToken = "new-login"))
        assertTrue(store.refreshIfCurrent("a", "new-login", PsnTokens("fresh", "refresh", 3600)))
        assertEquals("Player", store.account("a")?.onlineId)
        assertEquals("https://example.test/a.png", store.account("a")?.avatarUrl)
    }
}
