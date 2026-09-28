package com.dearby.nativeapp.shared.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
class ApiFailure(val status: Int, message: String) : Exception(message)
class HttpClient(private val baseUrl: String, private val allowHttp: Boolean, private val token: () -> String?) {
    val cacheNamespace: String = runCatching {
        val uri = URI(baseUrl).normalize()
        URI(uri.scheme?.lowercase(), null, uri.host?.lowercase(), uri.port, uri.path.trimEnd('/'), null, null).toString()
    }.getOrDefault("unconfigured")
    suspend fun request(method: String, path: String, body: String? = null, authenticated: Boolean = true): String = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "서비스 연결이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요." }
        val base = URI(baseUrl)
        require(base.scheme == "https" || (allowHttp && base.scheme == "http")) { "HTTPS 서버 주소가 필요합니다." }
        require(base.host != null && base.userInfo == null && base.query == null && base.fragment == null)
        val session = if (authenticated) token() ?: throw ApiFailure(401, "이메일 로그인이 필요합니다.") else null
        val connection = URI(baseUrl.trimEnd('/') + "/v1" + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            session?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            // Android URLConnection otherwise invents form-urlencoded for an empty DELETE.
            // Explicit empty text keeps the zero-byte contract and avoids unsupported/empty-JSON parsing.
            if (method == "DELETE" && body == null) connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { wireJson.parseToJsonElement(response).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }.getOrDefault("요청을 완료하지 못했습니다 ($status).")
                throw ApiFailure(status, message)
            }
            response
        } finally { connection.disconnect() }
    }
}
