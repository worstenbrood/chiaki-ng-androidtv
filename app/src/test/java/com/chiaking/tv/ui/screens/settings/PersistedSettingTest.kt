package com.chiaking.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PersistedSettingTest {
    @Test fun savedClampedValueSurvivesRecreationAndRefreshesExternalChanges() {
        val storage = mutableMapOf("volume" to 60)
        fun setting() = PersistedSetting({ storage.getValue("volume") }, { storage["volume"] = it.coerceIn(0, 100) })
        val state = setting()
        var volume by state
        volume = 200
        assertEquals(100, volume)
        val recreated by setting()
        assertEquals(100, recreated)
        storage["volume"] = 25
        state.refresh()
        assertEquals(25, volume)
    }

    @Test fun failedPersistenceDoesNotPublishUnsavedValue() {
        var value by PersistedSetting({ 42 }, { _: Int -> throw IllegalStateException("Write failed") })
        try { value = 99; fail("Expected failure") } catch(_: IllegalStateException) { }
        assertEquals(42, value)
    }
}
