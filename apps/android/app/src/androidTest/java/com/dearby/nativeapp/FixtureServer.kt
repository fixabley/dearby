package com.dearby.nativeapp

import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * Serves the catalog from [CatalogFixture] and a minimal account API to the app under test
 * (Debug `ApiOrigin.debugOverride`). Code [CODE] signs in; anything else is rejected.
 */
class FixtureServer : AutoCloseable {
    companion object { const val CODE = "123456" }
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    /** When true every request answers 503, to exercise the error and retry state. */
    @Volatile var failing = false
    /** When true `POST /v1/cards` answers 503 while the profile still saves. */
    @Volatile var failingCards = false
    /** "METHOD /path" of every request, in order. */
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile private var profile = """{"id":"p1","name":"","job":"","introduction":"","contacts":[],"histories":[],"updatedAt":"2026-10-06T00:00:00Z"}"""
    val origin get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    val input = it.getInputStream()
                    // Content-Length counts bytes, so read the request as bytes rather than characters.
                    fun line() = generateSequence { input.read().takeIf { byte -> byte >= 0 } }.takeWhile { byte -> byte != '\n'.code }
                        .map(Int::toByte).toList().toByteArray().decodeToString().trimEnd('\r')
                    val route = line().split(" ").take(2).joinToString(" ")
                    var length = 0
                    while (true) {
                        val header = line()
                        if (header.isEmpty()) break
                        if (header.lowercase().startsWith("content-length:")) length = header.substringAfter(":").trim().toInt()
                    }
                    val body = input.readNBytes(length).decodeToString()
                    val (status, reply) = respond(route, body)
                    val bytes = reply.toByteArray()
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }
    }
    private fun respond(route: String, body: String): Pair<Int, String> {
        requests += route
        if (failing) return 503 to "{}"
        val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        return when (route) {
            "GET /v1/catalog" -> 200 to CatalogFixture.body()
            "POST /v1/auth/challenges" -> 202 to """{"challengeId":"e1000000-0000-4000-8000-000000000001","expiresAt":"2026-10-06T00:05:00Z"}"""
            "POST /v1/auth/sessions" -> if (json.optString("code") == CODE) 200 to """{"sessionToken":"fixture-token","profileId":"p1"}""" else 401 to "{}"
            "GET /v1/profile" -> 200 to profile
            "PUT /v1/profile" -> { profile = json.put("id", "p1").put("updatedAt", "2026-10-06T00:00:00Z").toString(); 200 to profile }
            "POST /v1/cards" -> if (failingCards) 503 to "{}" else 201 to JSONObject().put("id", "c1000000-0000-4000-8000-000000000001").put("name", "내 명함")
                .put("description", "").put("profileName", JSONObject(profile).optString("name")).put("job", "")
                .put("contacts", org.json.JSONArray()).put("histories", org.json.JSONArray()).put("createdAt", "2026-10-06T00:00:00Z").toString()
            else -> 404 to "{}"
        }
    }
    override fun close() = socket.close()
}
