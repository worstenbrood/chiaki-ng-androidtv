// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

/** Join intent belongs to one stream; a cooldown never schedules an automatic retry. */
internal class CoopJoinRequests {
    private val wanted = mutableSetOf<Int>()
    private val retryAfter = mutableMapOf<Int, Long>()

    fun request(slot: Int, now: Long) {
        if(slot in 1 until PadManager.MAX_PADS && now >= (retryAfter[slot] ?: 0L))
            wanted.add(slot)
    }

    fun next(available: Set<Int>, now: Long): Int? = wanted.asSequence()
        .filter { it in available && now >= (retryAfter[it] ?: 0L) }.minOrNull()

    fun defer(slot: Int, now: Long, cooldownMs: Long) {
        wanted.remove(slot)
        retryAfter[slot] = now + cooldownMs
    }

    fun remove(slot: Int) {
        wanted.remove(slot)
        retryAfter.remove(slot)
    }

    fun clear() {
        wanted.clear()
        retryAfter.clear()
    }
}
