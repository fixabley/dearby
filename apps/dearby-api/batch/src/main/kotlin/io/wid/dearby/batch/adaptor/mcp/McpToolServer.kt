package io.wid.dearby.batch.adaptor.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.wid.dearby.batch.adaptor.claude.AgentToolbox
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.HexFormat

// 프로그램 하나의 수집 동안만 여는 최소 MCP 서버(streamable HTTP, JSON 응답만). 127.0.0.1의 임의 포트, 실행마다 바뀌는 비밀 경로로만 받는다.
// initialize·notifications/*·ping·tools/list·tools/call만 구현한다. 도구 처리는 AgentToolbox
class McpToolServer(private val toolbox: AgentToolbox, private val json: JsonMapper) : AutoCloseable {

    private val secret = HexFormat.of().formatHex(ByteArray(24).also { SecureRandom().nextBytes(it) })
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange -> exchange.use { handle(it) } }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}/$secret"

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestURI.path != "/$secret") return exchange.sendResponseHeaders(404, -1)
        // 서버가 먼저 보내는 스트림(GET)과 세션 종료(DELETE)는 쓰지 않는다
        if (exchange.requestMethod != "POST") return exchange.sendResponseHeaders(405, -1)
        val request = runCatching { json.readTree(exchange.requestBody.readAllBytes()) }.getOrNull()
            ?: return respond(exchange, error(null, -32700, "parse error"))
        // 알림(id 없음)에는 본문 없이 받았다고만 답한다
        if (!request.has("id")) return exchange.sendResponseHeaders(202, -1)
        val id = request.get("id")
        val params = request.path("params")
        val response = when (request.path("method").asString("")) {
            "initialize" -> result(
                id,
                mapOf(
                    "protocolVersion" to params.path("protocolVersion").asString(PROTOCOL_VERSION),
                    "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                    "serverInfo" to mapOf("name" to "dearby", "version" to "1"),
                ),
            )
            "ping" -> result(id, emptyMap<String, Any>())
            "tools/list" -> result(id, mapOf("tools" to AgentToolbox.DEFINITIONS))
            "tools/call" -> {
                val called = toolbox.call(params.path("name").asString(""), params.path("arguments"))
                result(id, mapOf("content" to listOf(mapOf("type" to "text", "text" to called.text)), "isError" to called.isError))
            }
            else -> error(id, -32601, "method not found")
        }
        respond(exchange, response)
    }

    private fun result(id: JsonNode?, value: Any) = mapOf("jsonrpc" to "2.0", "id" to id, "result" to value)

    private fun error(id: JsonNode?, code: Int, message: String) = mapOf("jsonrpc" to "2.0", "id" to id, "error" to mapOf("code" to code, "message" to message))

    private fun respond(exchange: HttpExchange, body: Any) {
        val bytes = json.writeValueAsBytes(body)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
    }
}
