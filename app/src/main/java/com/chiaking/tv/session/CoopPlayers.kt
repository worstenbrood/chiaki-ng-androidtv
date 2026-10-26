// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import android.os.SystemClock
import android.util.Base64
import android.widget.Toast
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnTokenStore
import com.metallic.chiaki.lib.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns account/pad assignments. Native code owns host acceptance and pending-slot reservations. */
internal class CoopPlayers(
    private val input: StreamInput,
    private val scope: CoroutineScope,
    private val currentSession: () -> Session?,
    private val connected: () -> Boolean,
    private val ps5: Boolean,
) {
    private val wanted = mutableSetOf<Int>()
    private val assigned = mutableMapOf<Int, String>()
    private val joined = mutableSetOf<Int>()
    private val retryAfter = mutableMapOf<Int, Long>()
    private var devices = input.padManager.slots
    private var native: Session? = null
    @Volatile private var job: Job? = null

    init {
        input.secondaryPadInputCallback = { slot -> if(connected()) wanted.add(slot) }
        input.slotStateChangedCallback = { slot, state ->
            if(connected() && slot in joined) native?.setControllerStateSlot(slot, state)
        }
        input.padSlotsChangedCallback = { slots ->
            for((slot, device) in devices)
                if(slot > 0 && slots[slot] != device) {
                    native?.removeUser(slot)
                    wanted.remove(slot)
                    assigned.remove(slot)
                    joined.remove(slot)
                    retryAfter.remove(slot)
                }
            devices = slots
        }
    }

    fun start() {
        if(job?.isActive == true) return
        job = scope.launch {
            while(true) {
                tick()
                delay(100)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun notify(slot: Int, message: Int) {
        Toast.makeText(input.context, input.context.getString(message, slot + 1), Toast.LENGTH_LONG).show()
    }

    private fun numericRid(rid: String): Long? = runCatching {
        val bytes = Base64.decode(rid, Base64.NO_WRAP)
        require(bytes.size == 8)
        var value = 0L
        for(i in bytes.indices) value = value or ((bytes[i].toLong() and 0xff) shl (8 * i))
        value.takeIf { it != 0L }
    }.getOrNull()

    private fun tick() {
        val session = currentSession()
        if(session !== native) {
            if(native != null) input.resetSecondaryStates()
            native = session
            assigned.clear()
            joined.clear()
            retryAfter.clear()
        }
        if(session == null || !connected()) return
        val now = SystemClock.elapsedRealtime()
        for(slot in assigned.keys.toList()) {
            val status = session.coopStatus(slot)
            if(status == 2 && joined.add(slot)) {
                session.setControllerStateSlot(slot, input.slotControllerState(slot))
                notify(slot, R.string.coop_joined)
            } else if(status < 0) {
                joined.remove(slot)
                assigned.remove(slot)
                retryAfter[slot] = now + 5000
                // A new input is required to retry: no automatic repeated host prompts.
                wanted.remove(slot)
                notify(slot, when(status) {
                    -2 -> R.string.coop_full
                    -3 -> R.string.coop_passcode
                    -4 -> R.string.coop_profile_required
                    -6 -> R.string.coop_timeout
                    else -> R.string.coop_refused
                })
            }
        }
        if(assigned.keys.any { session.coopStatus(it) == 1 }) return
        val slot = wanted.sorted().firstOrNull {
            it in devices && it !in assigned && now >= (retryAfter[it] ?: 0L)
        } ?: return
        if(!ps5) {
            wanted.remove(slot)
            retryAfter[slot] = now + 5000
            notify(slot, R.string.coop_ps5_only)
            return
        }
        val store = PsnTokenStore.get(input.context)
        val account = store.accounts().filter { it.rid != store.accountId }
            .sortedBy { it.rid }.firstOrNull { it.rid !in assigned.values && numericRid(it.rid) != null }
        if(account == null) {
            wanted.remove(slot)
            retryAfter[slot] = now + 5000
            notify(slot, R.string.coop_account_required)
            return
        }
        val result = session.addUser(slot, numericRid(account.rid)!!)
        if(result.isSuccess) assigned[slot] = account.rid
        else {
            // A cancelled/timed-out native join retains its slot until the host answers.
            retryAfter[slot] = now + 5000
            wanted.remove(slot)
            notify(slot, R.string.coop_refused)
        }
    }
}
