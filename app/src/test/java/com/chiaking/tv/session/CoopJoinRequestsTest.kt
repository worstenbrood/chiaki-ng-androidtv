package com.chiaking.tv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoopJoinRequestsTest {
    @Test fun cooldownRequiresAnotherPressRatherThanAutomaticallyRejoining() {
        val requests = CoopJoinRequests()
        requests.request(1, 100)
        assertEquals(1, requests.next(setOf(1), 100))
        requests.defer(1, 100, 5000)
        requests.request(1, 5099)
        assertNull(requests.next(setOf(1), 5100))
        requests.request(1, 5100)
        assertEquals(1, requests.next(setOf(1), 5100))
    }

    @Test fun reconnectDiscardsOldJoinIntentAndCooldowns() {
        val requests = CoopJoinRequests()
        requests.request(2, 100)
        requests.defer(1, 100, 5000)
        requests.clear()
        assertNull(requests.next(setOf(1, 2), 200))
        requests.request(1, 200)
        assertEquals(1, requests.next(setOf(1, 2), 200))
    }

    @Test fun removalAndEligibilityDoNotMoveAnotherControllersSlot() {
        val requests = CoopJoinRequests()
        for(slot in listOf(0, 1, 2, 7)) requests.request(slot, 100)
        assertEquals(2, requests.next(setOf(0, 2, 7), 100))
        requests.remove(1)
        assertEquals(2, requests.next(setOf(1, 2), 100))
        requests.remove(2)
        assertNull(requests.next(setOf(0, 1, 2, 7), 100))
    }
}
