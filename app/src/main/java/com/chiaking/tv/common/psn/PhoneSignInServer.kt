package com.chiaking.tv.common.psn

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** Short-lived LAN-only capability endpoint. It only receives one-use OAuth redirects, never passwords/tokens. */
internal class PhoneSignInServer(
    address: InetAddress,
    private val attempt: PsnLoginAttempt,
    private val render: (PhoneSignInStatus, Boolean, String) -> String,
    private val onCode: (String) -> Boolean,
    lifetimeMs: Long = 10 * 60 * 1000L,
) : Closeable {
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(address, 0), 4)
        soTimeout = 1000
    }
    private val path = "/${randomLoginSecret()}/"
    private val authority = "${address.hostAddress}:${server.localPort}"
    val url = "http://$authority$path"
    private val expiresAt = System.nanoTime() + lifetimeMs * 1_000_000L
    val status = AtomicReference(PhoneSignInStatus.WAITING)
    @Volatile private var active: Socket? = null

    fun serve() {
        try {
            while(!server.isClosed && System.nanoTime() < expiresAt) {
                try {
                    server.accept().use { socket ->
                        active = socket
                        if(server.isClosed) return@use
                        socket.soTimeout = 3000
                        try { handle(socket) } catch(_: IOException) { /* Disconnected or malformed request. */ }
                        finally { active = null }
                    }
                } catch(_: SocketTimeoutException) { /* Check expiry periodically. */ }
            }
        } catch(_: SocketException) { /* Dialog closed. */ }
        finally { close() }
    }

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val request = line(input).split(' ')
        if(request.size != 3 || request[2] != "HTTP/1.1") return respond(socket, 400)
        val headers = mutableMapOf<String, String>()
        var headerBytes = 0
        while(true) {
            val line = line(input)
            headerBytes += line.length + 2
            if(headerBytes > 16384) return respond(socket, 413)
            if(line.isEmpty()) break
            val name = line.substringBefore(':').lowercase(Locale.ROOT)
            if(':' !in line || headers.containsKey(name)) return respond(socket, 400)
            headers[name] = line.substringAfter(':').trim()
        }
        if(headers["host"] != authority || headers.containsKey("transfer-encoding")) return respond(socket, 403)
        val route = request[1]
        if(route != path && route != "${path}submit" && route != "${path}status") return respond(socket, 404)
        if(request[0] == "GET" && route != "${path}submit") {
            // Failed attempts can be retried with a newly obtained Sony redirect during this pairing session.
            if(route == path) status.compareAndSet(PhoneSignInStatus.FAILED, PhoneSignInStatus.WAITING)
            return page(socket)
        }
        if(request[0] != "POST" || route != "${path}submit") return respond(socket, 405)
        if(headers["origin"] != "http://$authority") return respond(socket, 403)
        if(headers["content-type"]?.substringBefore(';') != "application/x-www-form-urlencoded") return respond(socket, 415)
        val length = headers["content-length"]?.toIntOrNull() ?: return respond(socket, 411)
        if(length !in 1..16384) return respond(socket, 413)
        val body = ByteArray(length)
        var offset = 0
        while(offset < length) {
            val n = input.read(body, offset, length - offset)
            if(n < 0) throw IOException("Incomplete form")
            offset += n
        }
        val fields = try {
            body.toString(Charsets.UTF_8).split('&').map {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
        } catch(_: IllegalArgumentException) { return respond(socket, 400) }
        val code = if(fields.size == 1 && fields[0].first == "redirect") attempt.phoneCode(fields[0].second) else null
        if(code == null) return page(socket, invalid = true)
        if(status.compareAndSet(PhoneSignInStatus.WAITING, PhoneSignInStatus.PROCESSING)) {
            // A simultaneous TV submission may already own the login worker. Keep showing its progress.
            onCode(code)
        }
        respond(socket, 303, location = "${path}status")
    }

    private fun page(socket: Socket, invalid: Boolean = false) = respond(socket, 200,
        render(status.get(), invalid, path))

    private fun line(input: BufferedInputStream): String {
        val buffer = ByteArrayOutputStream()
        while(buffer.size() < 8192) {
            val b = input.read()
            if(b < 0) throw IOException("Incomplete HTTP headers")
            if(b == 10) return buffer.toString("US-ASCII").removeSuffix("\r")
            buffer.write(b)
        }
        throw IOException("HTTP line too long")
    }

    private fun respond(socket: Socket, code: Int, html: String = "", location: String? = null) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = if(code == 200) "OK" else if(code == 303) "See Other" else "Request rejected"
        val headers = "HTTP/1.1 $code $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n" +
            "Referrer-Policy: same-origin\r\nX-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n" +
            (location?.let { "Location: $it\r\n" } ?: "") + "\r\n"
        socket.getOutputStream().apply { write(headers.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }

    override fun close() {
        try { server.close() } catch(_: IOException) { }
        try { active?.close() } catch(_: IOException) { }
    }
}
