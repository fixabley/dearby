package com.dearby.nativeapp

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Serves `GET /v1/catalog` from [CatalogFixture] to the app under test (Debug `ApiOrigin.debugOverride`). */
class FixtureServer : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    /** When true every request answers 503, to exercise the error and retry state. */
    @Volatile var failing = false
    val origin get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    val request = it.getInputStream().bufferedReader().readLine().orEmpty()
                    val ok = !failing && request.startsWith("GET /v1/catalog ")
                    val body = (if (ok) CatalogFixture.body() else "{}").toByteArray()
                    it.getOutputStream().apply {
                        write(("HTTP/1.1 ${if (ok) "200 OK" else "503 Service Unavailable"}\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }
    }
    override fun close() = socket.close()
}
