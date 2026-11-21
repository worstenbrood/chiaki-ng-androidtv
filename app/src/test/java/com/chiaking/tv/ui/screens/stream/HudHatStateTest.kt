package com.chiaking.tv.ui.screens.stream

import org.junit.Assert.*
import org.junit.Test

class HudHatStateTest {
    @Test fun remoteAndGamepadCanNavigateInTheSameDirection() {
        val hats = HudHatState()
        assertTrue(hats.update(10, 20))
        assertTrue(hats.update(30, 20))
        assertFalse(hats.update(10, 20))
        assertFalse(hats.update(30, 20))
    }

    @Test fun centeringOneDeviceDoesNotReleaseAnotherDevicesHeldDirection() {
        val hats = HudHatState()
        assertTrue(hats.update(10, 20))
        assertFalse(hats.update(30, 0))
        assertFalse(hats.update(10, 20))
        assertFalse(hats.update(10, 0))
        assertTrue(hats.update(10, 20))
    }

    @Test fun closingHudReleasesAllHeldDirections() {
        val hats = HudHatState()
        assertTrue(hats.update(10, 20))
        hats.clear()
        assertTrue(hats.update(10, 20))
    }
}
