package com.chiaking.tv.common.settings

import com.chiaking.tv.common.MacAddress
import com.metallic.chiaki.lib.Target
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsDocumentIOTest {
    @Test fun backupRoundTripPreservesKeysAndManualHostAssociation() = runBlocking {
        val mac = MacAddress("01:23:45:67:89:ab")
        val original = SerializedSettings(listOf(SerializedRegisteredHost(Target.PS5_1, null, null,
            null, null, mac, "Console", byteArrayOf(0, -1, 42), 1, byteArrayOf(-128, 127))),
            listOf(SerializedManualHost("192.168.2.10", mac), SerializedManualHost("manual", null)))
        val caller = Thread.currentThread()
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        SettingsDocumentIO.write(exportSettings(original)) {
            assertNotSame(caller, Thread.currentThread())
            output
        }
        assertTrue(closed)
        var inputClosed = false
        val restored = SettingsDocumentIO.read {
            assertNotSame(caller, Thread.currentThread())
            object : ByteArrayInputStream(output.toByteArray()) {
                override fun close() { inputClosed = true; super.close() }
            }
        }
        assertTrue(inputClosed)
        val host = restored.registeredHosts.single()
        assertEquals(Target.PS5_1, host.target)
        assertEquals("Console", host.serverNickname)
        assertArrayEquals(original.registeredHosts.single().rpRegistKey, host.rpRegistKey)
        assertArrayEquals(original.registeredHosts.single().rpKey, host.rpKey)
        assertEquals(mac, host.serverMac)
        assertEquals(mac, restored.manualHosts.first().serverMac)
        assertEquals("192.168.2.10", restored.manualHosts.first().host)
        assertNull(restored.manualHosts.last().serverMac)
    }

    @Test fun missingStreamsAndFailedWritesPropagateAndClose() = runBlocking {
        try { SettingsDocumentIO.read { null }; fail("Expected read failure") } catch(_: IOException) { }
        try { SettingsDocumentIO.write("{}") { null }; fail("Expected write failure") } catch(_: IOException) { }
        var closed = false
        val output = object : OutputStream() {
            override fun write(b: Int) { throw IOException("Full") }
            override fun close() { closed = true }
        }
        try { SettingsDocumentIO.write("{}"){ output }; fail("Expected write failure") } catch(_: IOException) { }
        assertTrue(closed)
    }

    @Test fun unsupportedFormatAndVersionAreRejected() = runBlocking {
        for(json in listOf("{}", """{"format":"other","version":2,"settings":{}}""",
            """{"format":"chiaki-settings","version":3,"settings":{}}""")) {
            try { parseSettingsImport(Buffer().writeUtf8(json)); fail("Expected invalid backup") }
            catch(_: SettingsImportException) { }
        }
    }

    @Test fun cancellationIsNotConvertedToTransferFailure() = runBlocking {
        try {
            SettingsDocumentIO.write("{}") { throw CancellationException("Cancelled") }
            fail("Expected cancellation")
        } catch(_: CancellationException) { }
    }
}
