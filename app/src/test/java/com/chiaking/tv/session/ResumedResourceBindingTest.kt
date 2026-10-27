package com.chiaking.tv.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumedResourceBindingTest {
    private class Owner : LifecycleOwner {
        val observers = mutableSetOf<LifecycleObserver>()
        override val lifecycle = object : Lifecycle() {
            override val currentState = State.RESUMED
            override fun addObserver(observer: LifecycleObserver) {
                observers.add(observer)
                (observer as DefaultLifecycleObserver).onResume(this@Owner)
            }
            override fun removeObserver(observer: LifecycleObserver) { observers.remove(observer) }
        }
        fun pause() { observers.toList().forEach { (it as DefaultLifecycleObserver).onPause(this) } }
        fun resume() { observers.toList().forEach { (it as DefaultLifecycleObserver).onResume(this) } }
        fun destroy() { observers.toList().forEach { (it as DefaultLifecycleObserver).onDestroy(this) } }
    }

    @Test fun recreationDetachesOldOwnerAndStaleDisposalCannotStopNewOwner() {
        var starts = 0
        var stops = 0
        val binding = ResumedResourceBinding({ starts++ }, { stops++ })
        val old = Owner()
        val next = Owner()
        binding.attach(old)
        binding.attach(old)
        assertEquals(1, starts)
        binding.attach(next)
        assertTrue(old.observers.isEmpty())
        assertEquals(2, starts)
        assertEquals(1, stops)
        binding.detach(old)
        assertEquals(1, stops)
        next.pause()
        next.pause()
        assertEquals(2, stops)
        next.resume()
        assertEquals(3, starts)
        next.destroy()
        assertTrue(next.observers.isEmpty())
        assertEquals(3, stops)
        binding.dispose()
        assertEquals(3, stops)
    }
}
