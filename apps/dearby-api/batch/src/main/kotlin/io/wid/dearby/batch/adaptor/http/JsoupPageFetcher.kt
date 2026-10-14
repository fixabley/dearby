package io.wid.dearby.batch.adaptor.http

import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.PageFetchException
import io.wid.dearby.batch.application.PageFetcher
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

// 공식 페이지를 직접 받는다. 허용 호스트 밖으로의 리다이렉트·사설 IP·큰 응답은 거부한다(기존 수집기의 원문 경계).
// JavaScript로만 그려지는 페이지는 본문이 비어 근거 구절 대조에서 걸러진다
@Component
class JsoupPageFetcher : PageFetcher {

    private val client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun fetch(url: String, allowedHosts: List<String>): FetchedPage {
        var target = check(URI(url), allowedHosts)
        repeat(MAX_REDIRECTS + 1) {
            val response = client.send(
                HttpRequest.newBuilder(target).timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            if (response.statusCode() in 300..399) {
                response.body().close()
                val location = response.headers().firstValue("Location").orElseThrow { PageFetchException("리다이렉트 주소 없음") }
                target = check(target.resolve(location), allowedHosts)
                return@repeat
            }
            if (response.statusCode() != 200) {
                response.body().close()
                throw PageFetchException("HTTP ${response.statusCode()}")
            }
            val html = read(response)
            val document = Jsoup.parse(html, target.toString())
            return FetchedPage(
                url = target.toString(),
                text = document.body()?.text().orEmpty(),
                links = document.select("a[href]").map { it.absUrl("href") }.filter { it.isNotEmpty() }.toSet(),
            )
        }
        throw PageFetchException("리다이렉트가 ${MAX_REDIRECTS}번을 넘음")
    }

    private fun check(uri: URI, allowedHosts: List<String>): URI {
        if (uri.scheme != "https") throw PageFetchException("https가 아님: $uri")
        val host = uri.host?.lowercase() ?: throw PageFetchException("호스트 없음: $uri")
        if (host !in allowedHosts.map { it.lowercase() }) throw PageFetchException("허용 호스트 밖: $host")
        val addresses = InetAddress.getAllByName(host)
        if (addresses.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }) {
            throw PageFetchException("사설·내부 주소로 연결됨: $host")
        }
        return uri
    }

    private fun read(response: HttpResponse<java.io.InputStream>): String {
        val type = response.headers().firstValue("Content-Type").orElse("")
        if (!type.contains("html", ignoreCase = true)) {
            response.body().close()
            throw PageFetchException("HTML이 아님: $type")
        }
        val buffer = ByteArrayOutputStream()
        response.body().use { input ->
            val chunk = ByteArray(8192)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > MAX_BYTES) throw PageFetchException("${MAX_BYTES / 1_000_000} MB 초과")
            }
        }
        val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(type)?.groupValues?.get(1)
            ?.let { runCatching { charset(it) }.getOrNull() } ?: StandardCharsets.UTF_8
        return buffer.toString(charset)
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(15)
        const val MAX_REDIRECTS = 3
        const val MAX_BYTES = 3_000_000
        const val USER_AGENT = "DearbyCollector/1.0 (+https://dearby.wid.io.kr)"
    }
}
