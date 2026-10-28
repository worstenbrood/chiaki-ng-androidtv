package com.chiaking.tv.common.psn

import android.content.Context
import android.util.Log
import com.chiaking.tv.common.NetworkMonitor
import com.metallic.chiaki.lib.Holepunch
import com.metallic.chiaki.lib.HolepunchDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Shared by home and TV-channel discovery, including cold-start network recovery. */
internal object PsnDeviceRepository {
    suspend fun list(context: Context, rid: String): List<HolepunchDevice>? = withContext(Dispatchers.IO) {
        val accounts = PsnAccountRepository.get(context)
        retryPsnDeviceLookup(
            waitReady = {
                val network = NetworkMonitor.get(context)
                // Prefer validation, retaining the timeout fallback only when an active route exists.
                network.awaitValidated(15000) || network.hasInternetCapability
            },
            fetch = {
                val token = accounts.ensureFreshTokenFor(rid)
                if(token == null) null else {
                    val result = Holepunch.listDevices(token, true)
                    if(result.errorCode.isSuccess) result.devices else {
                        Log.w("PsnDevices", "PSN listDevices failed: ${result.errorCode.value}")
                        null
                    }
                }
            },
            onFailure = { Log.w("PsnDevices", "PSN lookup failed; retrying if attempts remain", it) }
        )
    }
}

/** Validation may precede endpoint DNS readiness; retry both native errors and thrown failures. */
internal suspend fun <T> retryPsnDeviceLookup(
    waitReady: suspend () -> Boolean,
    fetch: suspend () -> T?,
    pause: suspend (Long) -> Unit = { delay(it) },
    onFailure: (Exception) -> Unit = {},
): T? {
    repeat(5) { attempt ->
        if(attempt > 0) pause(2000L shl (attempt - 1))
        try {
            if(waitReady()) fetch()?.let { return it }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { onFailure(e) }
    }
    return null
}
