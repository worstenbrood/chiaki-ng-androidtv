package com.chiaking.tv.common.psn

import com.chiaking.tv.ui.screens.psn.phone.PhoneSignInPage
import com.chiaking.tv.ui.screens.psn.phone.PhoneSignInText
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSignInServerTest {
    private val text = PhoneSignInText("Title", "Instructions", "Sony", "Redirect", "Add", "Working", "Added", "Failed", "Invalid", "Retry")
    private val attempt = PsnLoginAttempt("uid", "nonce")

    private fun withServer(block: (PhoneSignInServer, AtomicInteger) -> Unit) {
        val received = AtomicInteger()
        val server = PhoneSignInServer(InetAddress.getByName("127.0.0.1"), attempt, { status, invalid, path -> PhoneSignInPage.render(text, path, attempt.loginUrl, status, invalid) },
            { code -> assertEquals("test-code", code); received.incrementAndGet(); true })
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit { server.serve() }
        try { block(server, received) }
        finally {
            server.close()
            try { worker.get(5, TimeUnit.SECONDS) } finally { executor.shutdownNow() }
        }
    }

    private fun request(server: PhoneSignInServer, method: String = "GET", suffix: String = "",
        body: String = "", origin: String? = null, host: String? = null, extra: String = ""): String {
        val uri = URI(server.url)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 3000
            val headers = "$method ${uri.path}$suffix HTTP/1.1\r\nHost: ${host ?: uri.rawAuthority}\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.toByteArray().size}\r\n" +
                (origin?.let { "Origin: $it\r\n" } ?: "") + extra + "\r\n"
            socket.getOutputStream().write((headers + body).toByteArray())
            socket.getInputStream().bufferedReader().readText()
        }
    }

    private fun form(state: String = "nonce") = "redirect=" + URLEncoder.encode(
        "${PsnAuth.REDIRECT_PAGE}?code=test-code&state=$state", "UTF-8")

    @Test(timeout = 10000) fun phonePageSubmitsOnceAndShowsCompletionWithoutEchoingCode() = withServer { server, received ->
        val page = request(server)
        assertTrue(page.startsWith("HTTP/1.1 200"))
        assertTrue(page.contains("Cache-Control: no-store"))
        assertTrue(page.contains("Referrer-Policy: same-origin"))
        assertTrue(page.contains("content=\"same-origin\""))
        assertTrue(page.contains("rel=\"noopener noreferrer\""))
        val origin = server.url.substringBefore(URI(server.url).path)
        repeat(2) { assertTrue(request(server, "POST", "submit", form(), origin).startsWith("HTTP/1.1 303")) }
        assertEquals(1, received.get())
        assertTrue(request(server, suffix = "status").contains("Working"))
        server.status.set(PhoneSignInStatus.SUCCESS)
        val success = request(server, suffix = "status")
        assertTrue(success.contains("Added"))
        assertFalse(success.contains("test-code"))
        assertFalse(success.contains("oauth/authorize"))
    }

    @Test(timeout = 10000) fun rejectsForeignOriginsHostsAndIncorrectAttemptState() = withServer { server, received ->
        assertTrue(request(server, host = "evil.test").startsWith("HTTP/1.1 403"))
        assertTrue(request(server, "POST", "submit", form(), "https://evil.test").startsWith("HTTP/1.1 403"))
        assertTrue(request(server, "POST", "submit", form()).startsWith("HTTP/1.1 403"))
        assertTrue(request(server, "POST", "submit", form(), "null").startsWith("HTTP/1.1 403"))
        val origin = server.url.substringBefore(URI(server.url).path)
        assertTrue(request(server, "POST", "submit", form("wrong"), origin).contains("Invalid"))
        assertTrue(request(server, suffix = "../").startsWith("HTTP/1.1 404"))
        assertTrue(request(server, extra = "Transfer-Encoding: chunked\r\n").startsWith("HTTP/1.1 403"))
        assertEquals(0, received.get())
    }

    @Test(timeout = 10000) fun expiredSessionClosesItsListeningSocket() {
        val server = PhoneSignInServer(InetAddress.getByName("127.0.0.1"), attempt, { status, invalid, path -> PhoneSignInPage.render(text, path, attempt.loginUrl, status, invalid) }, { true }, lifetimeMs = 1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit { server.serve() }.get(3, TimeUnit.SECONDS)
            val uri = URI(server.url)
            try { Socket(uri.host, uri.port).close(); throw AssertionError("Expired server still accepts connections") }
            catch(_: java.io.IOException) { }
        } finally { server.close(); executor.shutdownNow() }
    }

    @Test(timeout = 10000) fun simultaneousTvSubmissionKeepsPhoneOnProgressPage() {
        val server = PhoneSignInServer(InetAddress.getByName("127.0.0.1"), attempt,
            { status, _, _ -> status.name }, { false })
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit { server.serve() }
        try {
            val origin = server.url.substringBefore(URI(server.url).path)
            request(server, "POST", "submit", form(), origin)
            assertEquals(PhoneSignInStatus.PROCESSING, server.status.get())
        } finally {
            server.close()
            try { worker.get(5, TimeUnit.SECONDS) } finally { executor.shutdownNow() }
        }
    }

    @Test(timeout = 10000) fun closingDialogAlsoClosesAnIncompleteClientRequest() {
        val server = PhoneSignInServer(InetAddress.getByName("127.0.0.1"), attempt,
            { _, _, _ -> "page" }, { false })
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit { server.serve() }
        val uri = URI(server.url)
        try {
            Socket(uri.host, uri.port).use { client ->
                client.soTimeout = 1500
                client.getOutputStream().write("GET ${uri.path} HTTP/1.1\r\nHost:".toByteArray())
                server.close()
                worker.get(2, TimeUnit.SECONDS)
                try { assertEquals(-1, client.getInputStream().read()) }
                catch(_: java.net.SocketException) { /* A connection still in the accept queue is reset. */ }
            }
        } finally {
            server.close()
            try { worker.get(5, TimeUnit.SECONDS) } finally { executor.shutdownNow() }
        }
    }

    @Test fun htmlEscapesTranslatedTextAndAttributes() {
        val page = PhoneSignInPage.render(text.copy(title = "<script>bad</script>"), "/path/", "https://example.test?a=1&b=2", PhoneSignInStatus.WAITING)
        assertFalse(page.contains("<script>"))
        assertTrue(page.contains("&lt;script&gt;"))
        assertTrue(page.contains("a=1&amp;b=2"))
    }
}
