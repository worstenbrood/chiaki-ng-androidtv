package com.chiaking.tv.session

/** Platform-independent shortcut state; reset when its input device disconnects. */
internal class ShortcutKeyState<K> {
    private var combination: Set<K> = emptySet()
    private val pressed = mutableSetOf<K>()
    private var consuming = false

    fun reset() {
        pressed.clear()
        consuming = false
    }

    fun handle(keys: Set<K>, key: K, down: Boolean, fire: () -> Unit): Boolean {
        if(combination != keys) {
            reset()
            combination = keys.toSet()
        }
        if(key !in keys) return false
        if(down) pressed.add(key) else pressed.remove(key)
        val wasConsuming = consuming
        if(!consuming && down && pressed.containsAll(keys)) {
            consuming = true
            fire()
        }
        val handled = wasConsuming || consuming
        if(pressed.isEmpty()) consuming = false
        return handled
    }
}
