package com.chiaking.tv.session

import android.view.KeyEvent
import com.chiaking.tv.common.settings.Preferences
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.ControllerTouch

/** Consumes a completed shortcut through key release, so held buttons cannot leak to the game. */
internal class HudShortcut(private val preferences: Preferences, private val keyState: () -> ControllerState, private val onToggle: () -> Unit) {
    private val state = ShortcutKeyState<Int>()
    // cached: read on every key event, the shortcut only changes in settings
    private var comboShortcut: Preferences.MenuShortcut? = null
    private var comboKeysCached: Set<Int> = emptySet()
    private val comboKeys: Set<Int> get()
    {
        val shortcut = preferences.menuShortcut
        if(comboShortcut != shortcut)
        {
            comboShortcut = shortcut
            comboKeysCached = when(shortcut)
            {
                Preferences.MenuShortcut.L3_R3 -> setOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR)
                Preferences.MenuShortcut.SELECT_START -> setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)
                Preferences.MenuShortcut.SHOULDERS -> setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
                    KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2)
            }
        }
        return comboKeysCached
    }

    fun handle(event: KeyEvent): Boolean = state.handle(comboKeys, event.keyCode, event.action == KeyEvent.ACTION_DOWN) {
        clearComboButtons(comboKeys)
        onToggle()
    }
    fun reset() = state.reset()
    private fun clearComboButtons(combo: Set<Int>)
    {
        val state = keyState()
        var mask = 0U
        for(key in combo)
            mask = mask or when(key)
            {
                KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
                KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
                KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerState.BUTTON_TOUCHPAD
                KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
                KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
                KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
                else -> 0U
            }
        state.buttons = state.buttons and mask.inv()
        if(KeyEvent.KEYCODE_BUTTON_L2 in combo)
            state.l2State = 0U
        if(KeyEvent.KEYCODE_BUTTON_R2 in combo)
            state.r2State = 0U
        if(KeyEvent.KEYCODE_BUTTON_SELECT in combo)
            state.touches[0] = ControllerTouch(id = -1)
    }

}
