package com.chiaking.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

internal val LocalRouteOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("Route must be rendered inside a navigation entry")
}

/** Owns stores until their entries are popped, independently of composition and Activity instances. */
class RouteViewModelStores {
    private val owners = mutableMapOf<Screen, ViewModelStoreOwner>()

    fun owner(screen: Screen): ViewModelStoreOwner = owners.getOrPut(screen) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun retain(screens: Collection<Screen>) {
        val removed = owners.keys.filter { it !in screens }
        removed.forEach { owners.remove(it)?.viewModelStore?.clear() }
    }

    fun clear() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}

/** Uses the navigation entry's store, which survives temporary removal from composition. */
@Composable
internal inline fun <reified T : ViewModel> rememberRouteViewModel(crossinline factory: () -> T): T {
    return ViewModelProvider(LocalRouteOwner.current, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = factory() as V
    })[T::class.java]
}
