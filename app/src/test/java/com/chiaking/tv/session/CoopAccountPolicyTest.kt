package com.chiaking.tv.session

import com.chiaking.tv.common.psn.PsnAccountSummary
import org.junit.Assert.*
import org.junit.Test

class CoopAccountPolicyTest {
    private val a = PsnAccountSummary("AQAAAAAAAAA=", "A", "")
    private val b = PsnAccountSummary("AgAAAAAAAAA=", "B", "")
    private val c = PsnAccountSummary("AwAAAAAAAAA=", "C", "")

    @Test fun excludesActualOwnerAndDisablesAlreadyAssignedPlayers() {
        val options = coopAccountOptions(listOf(a, b, c), a.rid, setOf(b.rid))
        assertFalse(options.any { it.rid == a.rid })
        assertFalse(options.single { it.rid == b.rid }.available)
        assertTrue(options.single { it.rid == c.rid }.available)
    }

    @Test fun defaultAccountCanJoinAnotherAccountsLocalSessionAndUnknownOwnersAreNotGuessed() {
        assertTrue(coopAccountOptions(listOf(a, b), b.rid, emptySet()).single().available)
        assertEquals(listOf(a.rid, b.rid).toSet(), coopAccountOptions(listOf(a, b), null, emptySet()).map { it.rid }.toSet())
        assertFalse(coopAccountOptions(listOf(PsnAccountSummary("invalid", "", "")), null, emptySet()).single().available)
    }
}
