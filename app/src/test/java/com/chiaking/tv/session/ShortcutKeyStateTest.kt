package com.chiaking.tv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutKeyStateTest {
    @Test fun completeShortcutFiresOnceAndConsumesRepeatsAndReleases() {
        val state = ShortcutKeyState<Int>()
        var toggles = 0
        val keys = setOf(1, 2)
        fun key(code: Int, down: Boolean) = state.handle(keys, code, down) { toggles++ }
        assertFalse(key(1, true))
        assertTrue(key(2, true))
        assertTrue(key(2, true))
        assertTrue(key(1, false))
        assertTrue(key(1, true))
        assertEquals(1, toggles)
        assertTrue(key(1, false))
        assertTrue(key(2, false))
        assertFalse(key(1, true))
        assertTrue(key(2, true))
        assertEquals(2, toggles)
    }

    @Test fun deviceResetAndShortcutChangeDiscardHeldKeys() {
        val state = ShortcutKeyState<Int>()
        var toggles = 0
        state.handle(setOf(1, 2), 1, true) { toggles++ }
        state.reset()
        assertFalse(state.handle(setOf(1, 2), 2, true) { toggles++ })
        assertFalse(state.handle(setOf(2, 3), 3, true) { toggles++ })
        assertFalse(state.handle(setOf(2, 3), 9, true) { toggles++ })
        assertEquals(0, toggles)
        assertTrue(state.handle(setOf(2, 3), 2, true) { toggles++ })
        assertEquals(1, toggles)
    }
}
