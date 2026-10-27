package com.chiaking.tv.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Starts a resource only while its current owner is resumed and always releases it on detach. */
internal class ResumedResourceBinding(private val start: () -> Unit, private val stop: () -> Unit) {
    private var owner: LifecycleOwner? = null
    private var running = false
    private val observer = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if(!running) {
                running = true
                start()
            }
        }
        override fun onPause(owner: LifecycleOwner) = release()
        override fun onDestroy(owner: LifecycleOwner) = detach(owner)
    }

    fun attach(newOwner: LifecycleOwner) {
        if(owner === newOwner) return
        dispose()
        owner = newOwner
        newOwner.lifecycle.addObserver(observer)
    }

    fun detach(oldOwner: LifecycleOwner) {
        if(owner !== oldOwner) return
        oldOwner.lifecycle.removeObserver(observer)
        owner = null
        release()
    }

    private fun release() {
        if(running) {
            running = false
            stop()
        }
    }

    fun dispose() { owner?.let { detach(it) } }
}
