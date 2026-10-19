package io.wid.dearby.batch.application

import java.net.InetAddress
import java.net.URI

// 수집기가 밖으로 나가는 모든 주소의 공통 경계: https, 허용 호스트(있을 때) 정확히 일치, 사설·내부 IP 금지.
// HTML 받기(JsoupPageFetcher)와 브라우저 렌더링(PlaywrightPageFetcher)의 하위 요청이 같은 규칙을 쓴다
object UrlGuard {

    fun check(uri: URI, allowedHosts: List<String>): URI {
        if (uri.scheme != "https") throw PageFetchException("https가 아님: $uri")
        val host = uri.host?.lowercase() ?: throw PageFetchException("호스트 없음: $uri")
        if (!hostAllowed(host, allowedHosts)) throw PageFetchException("허용 호스트 밖: $host")
        if (resolvesToPrivate(host)) throw PageFetchException("사설·내부 주소로 연결됨: $host")
        return uri
    }

    fun hostAllowed(host: String, allowedHosts: List<String>): Boolean =
        allowedHosts.isEmpty() || host.lowercase() in allowedHosts.map { it.lowercase() }

    // DNS가 사설·루프백·링크로컬·any 주소를 돌려주면 true. 이름을 못 풀면 연결도 안 되므로 true로 본다
    fun resolvesToPrivate(host: String): Boolean = try {
        InetAddress.getAllByName(host).any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
    } catch (_: java.net.UnknownHostException) {
        true
    }
}
