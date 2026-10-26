// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadSlotsTest {
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
