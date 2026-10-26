// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

/** Device IDs keep their slot until unplugged; filling a gap never moves another player. */
internal class PadSlots(private val capacity: Int) {
    private val devices = mutableMapOf<Int, Int>()
    val assignments: Map<Int, Int> get() = devices.entries.associate { it.value to it.key }
    fun slotOf(device: Int): Int? = devices[device]

    fun update(connected: List<Int>) {
        devices.keys.retainAll(connected.toSet())
        for(device in connected) {
            if(device in devices) continue
            val free = (0 until capacity).firstOrNull { it !in devices.values } ?: break
            devices[device] = free
        }
    }
}
