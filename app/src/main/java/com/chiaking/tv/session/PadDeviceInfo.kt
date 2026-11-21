// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import android.view.InputDevice

/** Android device metadata kept separate from the platform for assignment regression tests. */
internal data class PadDeviceInfo(
    val id: Int,
    val descriptor: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val virtual: Boolean,
) {
    val isGamepad get() = isGamepadSource(sources, virtual)
    fun isSessionGamepad(navigationOnlyDescriptor: String) = isGamepad &&
        (navigationOnlyDescriptor.isEmpty() || descriptor != navigationOnlyDescriptor)
    // Bluetooth often reports the generic name "Wireless Controller"; use Sony HID IDs first.
    val isDualSense get() = vendorId == 0x054c && (productId == 0x0ce6 || productId == 0x0df2) ||
        (vendorId == 0 || vendorId == 0x054c) && name.contains("DualSense", ignoreCase = true)
}

internal fun isGamepadSource(sources: Int, virtual: Boolean): Boolean =
    !virtual && sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

internal fun preferredPrimaryPad(pads: List<PadDeviceInfo>, descriptor: String, currentPrimary: Int,
                                navigationOnlyDescriptor: String = ""): Int? {
    val eligible = pads.filter { it.isSessionGamepad(navigationOnlyDescriptor) }.sortedBy { it.id }
    val manual = eligible.filter { descriptor.isNotEmpty() && it.descriptor == descriptor }
    val candidates = manual.ifEmpty { eligible.filter { it.isDualSense } }
    return candidates.firstOrNull { it.id == currentPrimary }?.id ?: candidates.firstOrNull()?.id
}
