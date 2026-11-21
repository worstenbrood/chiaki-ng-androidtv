// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

/** Slots stay stable except when an explicitly preferred pad takes player 1. */
internal class PadSlots(private val capacity: Int) {
    private val devices = mutableMapOf<Int, Int>()
    val assignments: Map<Int, Int> get() = devices.entries.associate { it.value to it.key }
    fun slotOf(device: Int): Int? = devices[device]

    fun update(connected: List<Int>, preferredPrimary: Int? = null) {
        devices.keys.retainAll(connected.toSet())
        if(capacity > 0 && preferredPrimary != null && preferredPrimary in connected && devices[preferredPrimary] != 0) {
            val oldPrimary = assignments[0]
            val oldSlot = devices.remove(preferredPrimary)
            if(oldPrimary != null) devices.remove(oldPrimary)
            devices[preferredPrimary] = 0
            if(oldPrimary != null && oldSlot != null) devices[oldPrimary] = oldSlot
        }
        for(device in connected) {
            if(device in devices) continue
            val free = (0 until capacity).firstOrNull { it !in devices.values } ?: break
            devices[device] = free
        }
    }
}
