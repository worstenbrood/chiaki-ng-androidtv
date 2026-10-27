// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import android.os.SystemClock
import android.util.Base64
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.metallic.chiaki.lib.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns account/pad assignments. Native code owns host acceptance and pending-slot reservations. */
internal class CoopPlayers(
    private val input: StreamInput,
    private val scope: CoroutineScope,
    private val currentSession: () -> Session?,
    private val connected: () -> Boolean,
    private val ps5: Boolean,
    private val accounts: PsnAccountRepository,
) {
    private val wanted = mutableSetOf<Int>()
    private val assigned = mutableMapOf<Int, String>()
    private val joined = mutableSetOf<Int>()
    private val retryAfter = mutableMapOf<Int, Long>()
    private var devices = input.padManager.slots
    private var native: Session? = null
    @Volatile private var job: Job? = null
    private val _request = MutableStateFlow<CoopAccountRequest?>(null)
    val request = _request.asStateFlow()
    private val _notices = MutableSharedFlow<CoopNotice>(extraBufferCapacity = 16)
    val notices = _notices.asSharedFlow()
    private var nextChoiceId = 0L

    init {
        input.secondaryPadInputCallback = { slot ->
            if(connected() && slot !in assigned && SystemClock.elapsedRealtime() >= (retryAfter[slot] ?: 0L))
                wanted.add(slot)
        }
        input.slotStateChangedCallback = { slot, state ->
            if(connected() && slot in joined) native?.setControllerStateSlot(slot, state)
        }
        input.padSlotsChangedCallback = { slots ->
            for((slot, device) in devices)
                if(slot > 0 && slots[slot] != device) {
                    _request.value?.takeIf { it.slot == slot }?.let { cancelChoice(it.id) }
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
        _request.value = null
    }

    fun cancelChoice(id: Long) {
        val choice = _request.value?.takeIf { it.id == id } ?: return
        wanted.remove(choice.slot)
        retryAfter[choice.slot] = SystemClock.elapsedRealtime() + 500
        input.resetSecondaryState(choice.slot)
        _request.value = null
    }

    fun chooseAccount(id: Long, rid: String) {
        val choice = _request.value?.takeIf { it.id == id } ?: return
        if(choice.accounts.none { it.rid == rid && it.available }) return
        val store = accounts
        val session = currentSession()
        val numeric = numericRid(rid)
        if(!connected() || session == null || session !== native || choice.slot !in devices ||
            rid in assigned.values || rid == store.accountId || store.account(rid) == null || numeric == null) {
            cancelChoice(id)
            return
        }
        input.resetSecondaryState(choice.slot)
        _request.value = null
        val result = session.addUser(choice.slot, numeric)
        if(result.isSuccess) assigned[choice.slot] = rid
        else {
            retryAfter[choice.slot] = SystemClock.elapsedRealtime() + 5000
            wanted.remove(choice.slot)
            notify(choice.slot, CoopNoticeKind.REFUSED)
        }
    }

    private fun notify(slot: Int, message: CoopNoticeKind) {
        _notices.tryEmit(CoopNotice(slot, message))
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
            _request.value = null
            if(native != null) input.resetSecondaryStates()
            native = session
            assigned.clear()
            joined.clear()
            retryAfter.clear()
        }
        if(session == null || !connected()) {
            _request.value = null
            return
        }
        val now = SystemClock.elapsedRealtime()
        for(slot in assigned.keys.toList()) {
            val status = session.coopStatus(slot)
            if(status == 2 && joined.add(slot)) {
                session.setControllerStateSlot(slot, input.slotControllerState(slot))
                notify(slot, CoopNoticeKind.JOINED)
            } else if(status < 0) {
                joined.remove(slot)
                assigned.remove(slot)
                retryAfter[slot] = now + 5000
                // A new input is required to retry: no automatic repeated host prompts.
                wanted.remove(slot)
                notify(slot, when(status) {
                    -2 -> CoopNoticeKind.FULL
                    -3 -> CoopNoticeKind.PASSCODE
                    -4 -> CoopNoticeKind.PROFILE_REQUIRED
                    -6 -> CoopNoticeKind.TIMEOUT
                    else -> CoopNoticeKind.REFUSED
                })
            }
        }
        if(_request.value != null || assigned.keys.any { session.coopStatus(it) == 1 }) return
        val slot = wanted.sorted().firstOrNull {
            it in devices && it !in assigned && now >= (retryAfter[it] ?: 0L)
        } ?: return
        if(!ps5) {
            wanted.remove(slot)
            retryAfter[slot] = now + 5000
            notify(slot, CoopNoticeKind.PS5_ONLY)
            return
        }
        val store = accounts
        val accounts = store.accounts().filter { it.rid != store.accountId }.sortedBy { it.rid }
        val choice = CoopAccountRequest(++nextChoiceId, slot, accounts.map {
            CoopAccountOption(it.rid, it.rid !in assigned.values && numericRid(it.rid) != null, it.onlineId, it.avatarUrl)
        })
        input.resetSecondaryState(slot)
        _request.value = choice
    }
}
