package com.dearby.nativeapp

import com.dearby.nativeapp.shared.api.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture

/** Android URLConnection differs from the JVM implementation; keep DELETE framing interoperable. */
class AndroidHttpContractTest {
    @Test fun bodylessDeleteDoesNotInventFormContentType() = runBlocking {
        ServerSocket(0).use { server ->
            val headers = CompletableFuture<String>()
            Thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val request = buildList { while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; add(line) } }.joinToString("\n")
                    headers.complete(request)
                    socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }.start()
            HttpClient("http://127.0.0.1:${server.localPort}", true) { "fixture-token" }.request("DELETE", "/auth/session")
            val request = headers.get()
            assertTrue(request.startsWith("DELETE /v1/auth/session"))
            assertFalse("Empty DELETE must not use form encoding", request.contains("application/x-www-form-urlencoded"))
            assertTrue(request.contains("Content-Length: 0"))
            assertTrue(request.contains("Content-Type: text/plain; charset=UTF-8"))
        }
    }
}
