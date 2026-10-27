package com.chiaking.tv.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.chiaking.tv.session.StreamManager

/** Retains in-memory navigation across Activity recreation; sessions remain owned by StreamManager. */
class NavigationViewModel : ViewModel() {
    val screens = mutableStateListOf<Screen>(ScreenHome).apply {
        StreamManager.session?.let { add(ScreenStream(it)) }
    }
    val stores = RouteViewModelStores()

    override fun onCleared() {
        stores.clear()
    }
}
