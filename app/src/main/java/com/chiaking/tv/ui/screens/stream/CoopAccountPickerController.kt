package com.chiaking.tv.ui.screens.stream

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.session.CoopPlayers
import com.chiaking.tv.session.PadManager
import com.chiaking.tv.session.StreamInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Presentation state and Android navigation for a core co-op account request. */
internal class CoopAccountPickerController(
    private val players: CoopPlayers,
    private val input: StreamInput,
    private val accounts: PsnAccountRepository,
    scope: CoroutineScope,
) {
    private val _choice = MutableStateFlow<CoopAccountChoice?>(null)
    val choice = _choice.asStateFlow()
    private val choiceKeys = CoopChoiceKeys()
    private var hatDirection = 0
    private var profiles: Job? = null

    init {
        scope.launch {
            players.request.collect { request ->
                profiles?.cancel()
                choiceKeys.reset()
                hatDirection = 0
                _choice.value = request?.let { CoopAccountChoice(it.id, it.slot, it.accounts) }
                if(request != null) profiles = scope.launch {
                    for(account in request.accounts) {
                        val profile = accounts.refreshProfile(account.rid) ?: continue
                        val current = _choice.value?.takeIf { it.id == request.id } ?: return@launch
                        _choice.value = current.copy(accounts = current.accounts.map {
                            if(it.rid == profile.rid) it.copy(onlineId = profile.onlineId, avatarUrl = profile.avatarUrl) else it
                        })
                    }
                }
            }
        }
    }

    fun focusChoice(id: Long, index: Int) {
        _choice.value?.takeIf { it.id == id }?.let { _choice.value = it.focus(index) }
    }
    fun cancelChoice(id: Long) {
        if(_choice.value?.id != id) return
        profiles?.cancel()
        _choice.value = null
        players.cancelChoice(id)
    }
    fun chooseAccount(id: Long, rid: String) {
        if(_choice.value?.id != id) return
        profiles?.cancel()
        _choice.value = null
        players.chooseAccount(id, rid)
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

}
