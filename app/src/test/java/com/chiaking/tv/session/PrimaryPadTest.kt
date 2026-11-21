// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import android.view.InputDevice
import org.junit.Assert.*
import org.junit.Test

class PrimaryPadTest {
    @Test fun joystickShapedRemoteCanRemainNavigationOnlyWithoutConsumingASlot() {
        val remote = pad(10, "TCL Remote", descriptor = "tcl")
        val dualSense = pad(20, "DualSense")
        val xbox = pad(30, "Xbox")
        val devices = listOf(remote, dualSense, xbox)
        assertTrue(remote.isGamepad)
        assertFalse(remote.isSessionGamepad("tcl"))
        val slots = PadSlots(7)
        slots.update(devices.filter { it.isSessionGamepad("tcl") }.map { it.id },
            preferredPrimaryPad(devices, "", -1, "tcl"))
        assertEquals(mapOf(0 to 20, 1 to 30), slots.assignments)
        assertNull(slots.slotOf(remote.id))
        val joins = CoopJoinRequests()
        slots.slotOf(dualSense.id)?.let { joins.request(it, 0) }
        slots.slotOf(remote.id)?.let { joins.request(it, 0) }
        assertNull(joins.next(slots.assignments.keys, 0))
        joins.request(slots.slotOf(xbox.id)!!, 0)
        assertEquals(1, joins.next(slots.assignments.keys, 0))
    }

    @Test fun navigationOnlyOverrideSurvivesReconnectAndExcludesManualPrimary() {
        val remote = pad(100, "TCL Remote", descriptor = "tcl")
        assertFalse(remote.isSessionGamepad("tcl"))
        assertTrue(remote.isSessionGamepad(""))
        assertNull(preferredPrimaryPad(listOf(remote), "tcl", -1, "tcl"))
    }

    private fun pad(id: Int, name: String = "Gamepad", vendor: Int = 0, product: Int = 0,
                    sources: Int = InputDevice.SOURCE_JOYSTICK, virtual: Boolean = false,
                    descriptor: String = "pad-$id") =
        PadDeviceInfo(id, descriptor, name, vendor, product, sources, virtual)

    @Test fun sonyIdsRecognizeGenericWirelessControllerAndEdge() {
        assertTrue(pad(1, "Wireless Controller", 0x054c, 0x0ce6).isDualSense)
        assertTrue(pad(2, "Wireless Controller", 0x054c, 0x0df2).isDualSense)
        assertFalse(pad(3, "Wireless Controller", 0x054c, 0x09cc).isDualSense)
        assertFalse(pad(4, vendor = 0x045e, product = 0x0ce6).isDualSense)
    }

    @Test fun dualSenseNameIsFallbackWhenAndroidOmitsUsbIds() {
        assertTrue(pad(1, "Sony DualSense Wireless Controller").isDualSense)
        assertTrue(pad(2, "dualsense edge").isDualSense)
        assertFalse(pad(3, "Wireless Controller").isDualSense)
    }

    @Test fun remotesKeyboardsAndVirtualDevicesAreNotPads() {
        assertFalse(pad(1, "TCL Remote", sources = InputDevice.SOURCE_DPAD or InputDevice.SOURCE_GAMEPAD).isGamepad)
        assertFalse(pad(2, sources = InputDevice.SOURCE_KEYBOARD).isGamepad)
        assertFalse(pad(3, virtual = true).isGamepad)
        assertFalse(pad(4, sources = InputDevice.SOURCE_CLASS_JOYSTICK).isGamepad)
        assertTrue(pad(5, sources = InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD).isGamepad)
    }

    @Test fun dualSenseWinsOverEarlierRemoteReportingJoystick() {
        val pads = listOf(pad(10, "TCL Remote"), pad(20, "Wireless Controller", 0x054c, 0x0ce6))
        assertEquals(20, preferredPrimaryPad(pads, "", 10))
        val slots = PadSlots(7)
        slots.update(pads.map { it.id }, preferredPrimaryPad(pads, "", -1))
        val joins = CoopJoinRequests()
        joins.request(slots.slotOf(20)!!, 0)
        assertEquals(0, slots.slotOf(20))
        assertNull(joins.next(slots.assignments.keys, 0))
    }

    @Test fun manualSelectionOverridesSonyPriorityAndSurvivesNewAndroidId() {
        val sony = pad(20, "DualSense")
        assertEquals(10, preferredPrimaryPad(listOf(pad(10), sony), "pad-10", 20))
        assertEquals(100, preferredPrimaryPad(listOf(pad(100, descriptor = "pad-10"), sony), "pad-10", 20))
    }

    @Test fun disconnectedManualSelectionFallsBackToDualSense() {
        assertEquals(20, preferredPrimaryPad(listOf(pad(10), pad(20, "DualSense")), "missing", 10))
        assertNull(preferredPrimaryPad(listOf(pad(10), pad(30)), "missing", 10))
        assertNull(preferredPrimaryPad(emptyList(), "", -1))
    }

    @Test fun twoDualSensePadsKeepCurrentPrimaryAcrossEnumerationChanges() {
        val first = pad(10, "DualSense")
        val second = pad(20, "DualSense")
        assertEquals(10, preferredPrimaryPad(listOf(second, first), "", 10))
        assertEquals(10, preferredPrimaryPad(listOf(second, first), "", -1))
    }

    @Test fun virtualSonyAndNonGamepadCollectionsNeverBecomePrimary() {
        val fake = pad(1, "DualSense", virtual = true)
        val touchpad = pad(2, "DualSense", sources = InputDevice.SOURCE_TOUCHPAD)
        assertNull(preferredPrimaryPad(listOf(fake, touchpad), "pad-1", -1))
    }

    @Test fun indistinguishableDescriptorsKeepCurrentPrimary() {
        val pads = listOf(pad(20, descriptor = "same"), pad(10, descriptor = "same"))
        assertEquals(10, preferredPrimaryPad(pads, "same", 10))
        assertEquals(10, preferredPrimaryPad(pads, "same", -1))
    }
}
