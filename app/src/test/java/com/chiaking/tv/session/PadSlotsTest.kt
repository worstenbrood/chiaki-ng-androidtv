// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadSlotsTest {
    @Test fun preferredPadStartsAsPlayerOneRegardlessOfAndroidOrder() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20, 30), preferredPrimary = 20)
        assertEquals(0, slots.slotOf(20))
        assertEquals(3, slots.assignments.size)
    }

    @Test fun latePreferredPadDisplacesOnlyThePrimary() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20, 30))
        slots.update(listOf(10, 20, 30, 40), preferredPrimary = 40)
        assertEquals(mapOf(0 to 40, 1 to 20, 2 to 30, 3 to 10), slots.assignments)
    }

    @Test fun promotingExistingSecondarySwapsOnlyItsSlotWithPrimary() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20, 30))
        slots.update(listOf(30, 20, 10), preferredPrimary = 20)
        assertEquals(mapOf(0 to 20, 1 to 10, 2 to 30), slots.assignments)
    }

    @Test fun preferredPadCanJoinEvenAtCapacity() {
        val slots = PadSlots(3)
        slots.update(listOf(10, 20, 30))
        slots.update(listOf(10, 20, 30, 40), preferredPrimary = 40)
        assertEquals(mapOf(0 to 40, 1 to 20, 2 to 30), slots.assignments)
        assertNull(slots.slotOf(10))
    }

    @Test fun disconnectedPreferenceDoesNotEvictPrimary() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20), preferredPrimary = 99)
        assertEquals(mapOf(0 to 10, 1 to 20), slots.assignments)
    }

    @Test fun reconnectingPreferredPadWithNewIdRestoresPrimary() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20), preferredPrimary = 20)
        slots.update(listOf(10))
        slots.update(listOf(10, 200), preferredPrimary = 200)
        assertEquals(mapOf(0 to 200, 1 to 10), slots.assignments)
    }

    @Test fun emptyAndZeroCapacityAreSafe() {
        val slots = PadSlots(0)
        slots.update(listOf(10), preferredPrimary = 10)
        assertEquals(emptyMap<Int, Int>(), slots.assignments)
        slots.update(emptyList())
        assertEquals(emptyMap<Int, Int>(), slots.assignments)
    }

    @Test fun enumerationOrderDoesNotChangePlayers() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20, 30))
        slots.update(listOf(30, 10, 20))
        assertEquals(mapOf(0 to 10, 1 to 20, 2 to 30), slots.assignments)
    }

    @Test fun unpluggingPrimaryAndSecondaryDoesNotPromoteOthers() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20, 30))
        slots.update(listOf(10, 30))
        assertEquals(2, slots.slotOf(30))
        slots.update(listOf(30))
        assertEquals(mapOf(2 to 30), slots.assignments)
        assertNull(slots.slotOf(10))
    }

    @Test fun newDevicesFillVacanciesWithoutMovingSurvivors() {
        val slots = PadSlots(7)
        slots.update(listOf(10, 20, 30))
        slots.update(listOf(30, 40, 50))
        assertEquals(mapOf(0 to 40, 1 to 50, 2 to 30), slots.assignments)
    }

    @Test fun capacityAndDuplicateDeviceIdsNeverCreateInvalidSlots() {
        val slots = PadSlots(3)
        slots.update(listOf(10, 10, 20, 30, 40))
        assertEquals(mapOf(0 to 10, 1 to 20, 2 to 30), slots.assignments)
        assertNull(slots.slotOf(40))
        slots.update(listOf(20, 30, 40))
        assertEquals(0, slots.slotOf(40))
        assertEquals(1, slots.slotOf(20))
    }
}
