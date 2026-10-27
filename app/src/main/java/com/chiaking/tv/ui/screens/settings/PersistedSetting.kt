package com.chiaking.tv.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Re-reads the saved value so validation/clamping and external changes are reflected in the UI. */
internal class PersistedSetting<T>(private val read: () -> T, private val write: (T) -> Unit) : ReadWriteProperty<Any?, T> {
    private var value by mutableStateOf(read())

    fun refresh() { value = read() }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        write(value)
        refresh()
    }
}
