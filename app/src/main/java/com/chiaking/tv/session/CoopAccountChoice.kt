package com.chiaking.tv.session

/** Account identifiers only: never expose PSN tokens to the stream UI. */
data class CoopAccountOption(
    val rid: String,
    val available: Boolean,
)

data class CoopAccountChoice(
    val id: Long,
    val slot: Int,
    val accounts: List<CoopAccountOption>,
    val selectedIndex: Int = accounts.indexOfFirst { it.available }.let { if(it < 0) accounts.size else it },
) {
    // The last item is always Cancel, even when every account is already in use.
    fun canFocus(index: Int) = index == accounts.size || accounts.getOrNull(index)?.available == true
    fun move(direction: Int): CoopAccountChoice {
        val next = if(direction > 0) (selectedIndex + 1..accounts.size).firstOrNull(::canFocus)
            else (selectedIndex - 1 downTo 0).firstOrNull(::canFocus)
        return if(next == null) this else copy(selectedIndex = next)
    }
    fun focus(index: Int) = if(canFocus(index)) copy(selectedIndex = index) else this
}

/** Only releases with a matching fresh press inside this picker may confirm/cancel. */
internal class CoopChoiceKeys {
    private val pressed = mutableSetOf<Pair<Int, Int>>()
    fun down(device: Int, key: Int, repeatCount: Int) {
        if(repeatCount == 0) pressed.add(device to key)
    }
    fun up(device: Int, key: Int) = pressed.remove(device to key)
    fun reset() = pressed.clear()
}
