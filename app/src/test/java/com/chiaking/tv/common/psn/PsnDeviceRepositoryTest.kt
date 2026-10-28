package com.chiaking.tv.common.psn

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PsnDeviceRepositoryTest {
    @Test fun waitsForNetworkAgainAfterTimeoutAndRetriesNativeFailure() = runBlocking {
        var readinessChecks = 0
        var requests = 0
        val waits = mutableListOf<Long>()
        val result = retryPsnDeviceLookup(
            waitReady = { ++readinessChecks > 1 },
            fetch = { if(++requests == 1) null else listOf("console") },
            pause = { waits.add(it) }
        )
        assertEquals(listOf("console"), result)
        assertEquals(3, readinessChecks)
        assertEquals(2, requests)
        assertEquals(listOf(2000L, 4000L), waits)
    }

    @Test fun transientExceptionIsRetriedAndEmptySuccessfulListIsValid() = runBlocking {
        var requests = 0
        val result = retryPsnDeviceLookup(waitReady = { true }, fetch = {
            if(++requests == 1) throw IOException("DNS not ready")
            emptyList<String>()
        }, pause = {})
        assertEquals(emptyList<String>(), result)
        assertEquals(2, requests)
    }

    @Test fun offlineNeverCallsPsnAndStopsAfterBoundedAttempts() = runBlocking {
        var checks = 0
        val result = retryPsnDeviceLookup<String>(waitReady = { checks++; false },
            fetch = { fail("Must not call PSN while offline"); null }, pause = {})
        assertNull(result)
        assertEquals(5, checks)
    }

    @Test fun cancellationStopsWithoutAnotherRequest() = runBlocking {
        var requests = 0
        try {
            retryPsnDeviceLookup<String>(waitReady = { true }, fetch = {
                requests++
                throw CancellationException("Account switched")
            }, pause = { fail("Cancellation must not retry") })
            fail("Expected cancellation")
        } catch(_: CancellationException) { }
        assertEquals(1, requests)
    }
}
