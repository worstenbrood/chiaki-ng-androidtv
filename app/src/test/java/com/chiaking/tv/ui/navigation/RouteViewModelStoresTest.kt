package com.chiaking.tv.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteViewModelStoresTest {
    private class ResourceModel : ViewModel() {
        var closed = false
        override fun onCleared() { closed = true }
    }

    private fun model(owner: ViewModelStoreOwner): ResourceModel = ViewModelProvider(owner,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ResourceModel() as T
        })[ResourceModel::class.java]

    @Test fun coveredEntryRetainsModelAndPoppedEntryReleasesResources() {
        val stores = RouteViewModelStores()
        val home = model(stores.owner(ScreenHome))
        val settings = model(stores.owner(ScreenSettings))
        stores.retain(listOf(ScreenHome, ScreenSettings))
        assertSame(home, model(stores.owner(ScreenHome)))
        assertFalse(home.closed)
        stores.retain(listOf(ScreenHome))
        assertTrue(settings.closed)
        assertFalse(home.closed)
        assertNotSame(settings, model(stores.owner(ScreenSettings)))
        stores.clear()
        assertTrue(home.closed)
    }

    @Test fun distinctEntriesHaveIndependentStores() {
        val stores = RouteViewModelStores()
        val first = ScreenRegist("one", true)
        val second = ScreenRegist("two", true)
        val a = model(stores.owner(first))
        val b = model(stores.owner(second))
        assertNotSame(a, b)
        stores.retain(listOf(second))
        assertTrue(a.closed)
        assertFalse(b.closed)
        stores.clear()
        assertTrue(b.closed)
    }
}
