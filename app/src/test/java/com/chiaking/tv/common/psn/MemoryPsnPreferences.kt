package com.chiaking.tv.common.psn

import android.content.SharedPreferences
import java.lang.reflect.Proxy

/** Test the SharedPreferences contract without an Android device or encrypted test secrets. */
internal class MemoryPsnPreferences {
    val values = mutableMapOf<String, Any?>()
    val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when(method.name) {
            "edit" -> editor()
            "getAll" -> values.toMap()
            "contains" -> values.containsKey(args!![0] as String)
            "getString", "getLong", "getStringSet" -> values[args!![0] as String] ?: args[1]
            else -> error("Unexpected SharedPreferences method ${method.name}")
        }
    } as SharedPreferences

    private fun editor(): SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        lateinit var proxy: SharedPreferences.Editor
        proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when(method.name) {
                "putString", "putLong", "putStringSet" -> {
                    changes[args!![0] as String] = args[1]
                    proxy
                }
                "remove" -> { changes[args!![0] as String] = null; proxy }
                "apply", "commit" -> {
                    changes.forEach { (key, value) ->
                        if(value == null) values.remove(key) else values[key] = value
                    }
                    if(method.name == "commit") true else null
                }
                else -> error("Unexpected Editor method ${method.name}")
            }
        } as SharedPreferences.Editor
        return proxy
    }
}
