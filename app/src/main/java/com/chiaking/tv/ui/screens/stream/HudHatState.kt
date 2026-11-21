package com.chiaking.tv.ui.screens.stream

/** One navigation step per hat press, independently for the remote and each gamepad. */
internal class HudHatState {
    private val directions = mutableMapOf<Int, Int>()
    fun clear() { directions.clear() }
    fun update(deviceId: Int, next: Int): Boolean {
        if(next == 0) {
            directions.remove(deviceId)
            return false
        }
        return directions.put(deviceId, next) != next
    }
}
