// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.session

import android.os.SystemClock
import android.util.Base64
import android.widget.Toast
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnTokenStore
import com.metallic.chiaki.lib.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val _choice = MutableStateFlow<CoopAccountChoice?>(null)
    val choice = _choice.asStateFlow()
    private var nextChoiceId = 0L
    private val choiceKeys = CoopChoiceKeys()
    private var hatDirection = 0

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
                    _choice.value?.takeIf { it.slot == slot }?.let { cancelChoice(it.id) }
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
        _choice.value = null
    }

    fun focusChoice(id: Long, index: Int) {
        _choice.value?.takeIf { it.id == id }?.let { _choice.value = it.focus(index) }
    }

    fun cancelChoice(id: Long) {
        val choice = _choice.value?.takeIf { it.id == id } ?: return
        wanted.remove(choice.slot)
        retryAfter[choice.slot] = SystemClock.elapsedRealtime() + 500
        input.resetSecondaryState(choice.slot)
        _choice.value = null
    }

    fun chooseAccount(id: Long, rid: String) {
        val choice = _choice.value?.takeIf { it.id == id } ?: return
        if(choice.accounts.none { it.rid == rid && it.available }) return
        val store = PsnTokenStore.get(input.context)
        val session = currentSession()
        val numeric = numericRid(rid)
        if(!connected() || session == null || session !== native || choice.slot !in devices ||
            rid in assigned.values || rid == store.accountId || store.account(rid) == null || numeric == null) {
            cancelChoice(id)
            return
        }
        input.resetSecondaryState(choice.slot)
        _choice.value = null
        val result = session.addUser(choice.slot, numeric)
        if(result.isSuccess) assigned[choice.slot] = rid
        else {
            retryAfter[choice.slot] = SystemClock.elapsedRealtime() + 5000
            wanted.remove(choice.slot)
            notify(choice.slot, R.string.coop_refused)
        }
    }

    private fun acceptsDevice(deviceId: Int, device: InputDevice?): Boolean {
        val choice = _choice.value ?: return false
        return !PadManager.isGamepad(device) || input.padManager.slotOf(deviceId) == choice.slot
    }

    fun handleKey(event: KeyEvent): Boolean {
        val choice = _choice.value ?: return false
        if(!acceptsDevice(event.deviceId, event.device)) return false
        if(event.action == KeyEvent.ACTION_DOWN) {
            choiceKeys.down(event.deviceId, event.keyCode, event.repeatCount)
            val direction = when(event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> 1
                else -> 0
            }
            if(direction != 0) _choice.value = choice.move(direction)
        } else if(event.action == KeyEvent.ACTION_UP) {
            if(!choiceKeys.up(event.deviceId, event.keyCode)) return true
            when(event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> cancelChoice(choice.id)
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val account = choice.accounts.getOrNull(choice.selectedIndex)
                    if(account == null) cancelChoice(choice.id) else chooseAccount(choice.id, account.rid)
                }
            }
        }
        return true
    }

    fun handleMotion(event: MotionEvent): Boolean {
        val choice = _choice.value ?: return false
        if(!PadManager.isGamepad(event.device)) return false
        if(!acceptsDevice(event.deviceId, event.device)) return false
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X).takeIf { kotlin.math.abs(it) > 0.5f }
            ?: event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y).takeIf { kotlin.math.abs(it) > 0.5f }
            ?: event.getAxisValue(MotionEvent.AXIS_Y)
        val direction = when {
            x < -0.5f || y < -0.5f -> -1
            x > 0.5f || y > 0.5f -> 1
            else -> 0
        }
        if(direction != 0 && direction != hatDirection) _choice.value = choice.move(direction)
        hatDirection = direction
        return true
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
            _choice.value = null
            if(native != null) input.resetSecondaryStates()
            native = session
            assigned.clear()
            joined.clear()
            retryAfter.clear()
        }
        if(session == null || !connected()) {
            _choice.value = null
            return
        }
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
        if(_choice.value != null || assigned.keys.any { session.coopStatus(it) == 1 }) return
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
        val accounts = store.accounts().filter { it.rid != store.accountId }.sortedBy { it.rid }
        val choice = CoopAccountChoice(++nextChoiceId, slot, accounts.map {
            CoopAccountOption(it.rid, it.rid !in assigned.values && numericRid(it.rid) != null)
        })
        input.resetSecondaryState(slot)
        choiceKeys.reset()
        hatDirection = 0
        _choice.value = choice
    }
}
