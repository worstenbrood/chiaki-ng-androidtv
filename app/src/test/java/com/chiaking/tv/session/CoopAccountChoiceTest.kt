package com.chiaking.tv.session

import org.junit.Assert.*
import org.junit.Test

class CoopAccountChoiceTest {
    @Test fun openingPressAndOtherDevicesCannotConfirmByReleasingAHeldButton() {
        val keys = CoopChoiceKeys()
        assertFalse(keys.up(10, 96)) // opening Cross press happened before the picker
        keys.down(10, 96, 1) // its held-button repeats also cannot arm confirmation
        assertFalse(keys.up(10, 96))
        keys.down(20, 96, 0)
        assertFalse(keys.up(10, 96)) // a remote cannot arm another device's release
        assertTrue(keys.up(20, 96))
        assertFalse(keys.up(20, 96))
        keys.down(10, 96, 0)
        keys.reset()
        assertFalse(keys.up(10, 96)) // next player's picker starts unarmed
    }
    @Test fun startsAtFirstAvailableAccountAndSkipsOccupiedAccounts() {
        val choice = CoopAccountChoice(1, 1, listOf(
            CoopAccountOption("used", false), CoopAccountOption("a", true),
            CoopAccountOption("used-too", false), CoopAccountOption("b", true)))
        assertEquals(1, choice.selectedIndex)
        assertEquals(3, choice.move(1).selectedIndex)
        assertEquals(4, choice.move(1).move(1).selectedIndex)
        assertEquals(3, choice.move(1).move(1).move(-1).selectedIndex)
        assertEquals(1, choice.move(-1).selectedIndex)
        assertEquals(choice, choice.focus(0))
    }

    @Test fun cancelRemainsSelectableWhenNoAccountIsAvailable() {
        val empty = CoopAccountChoice(1, 2, emptyList())
        assertEquals(0, empty.selectedIndex)
        assertEquals(empty, empty.move(1))
        assertEquals(empty, empty.move(-1))
        val occupied = CoopAccountChoice(2, 3, listOf(CoopAccountOption("a", false)))
        assertEquals(1, occupied.selectedIndex)
        assertEquals(occupied, occupied.move(-1))
    }

    @Test fun metadataUpdatesPreserveSelectedAccountIdentityAndRequest() {
        val choice = CoopAccountChoice(7, 2,
            listOf(CoopAccountOption("a", true), CoopAccountOption("b", true))).focus(1)
        val updated = choice.copy(accounts = choice.accounts.map { it.copy(onlineId = "Name-${it.rid}") })
        assertEquals("b", updated.accounts[updated.selectedIndex].rid)
        assertEquals(7L, updated.id)
        assertEquals(2, updated.slot)
        assertEquals(updated, updated.focus(-1))
        assertEquals(updated, updated.focus(99))
    }
}
