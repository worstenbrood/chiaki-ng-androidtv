package com.chiaking.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/** Screen-stack entries leave composition when popped: clear their resources at that boundary. */
@Composable
inline fun <reified T : ViewModel> rememberRouteViewModel(crossinline factory: () -> T): T {
    val store = remember { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    return remember(store) {
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = factory() as V
        })[T::class.java]
    }
}
