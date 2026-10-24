package io.wid.dearby.batch.application

import java.net.InetAddress
import java.net.URI

// 수집기가 밖으로 나가는 모든 주소의 공통 경계: https(http는 허용 호스트에 이름이 있는 호스트만), 허용 호스트(있을 때) 정확히 일치, 사설·내부 IP 금지.
// HTML 받기(JsoupPageFetcher)와 브라우저 렌더링(PlaywrightPageFetcher)의 하위 요청이 같은 규칙을 쓴다
object UrlGuard {

    fun check(uri: URI, allowedHosts: List<String>): URI {
        val host = uri.host?.lowercase() ?: throw PageFetchException("호스트 없음: $uri")
        if (!schemeAllowed(uri.scheme, host, allowedHosts)) throw PageFetchException("https가 아님: $uri")
        if (!hostAllowed(host, allowedHosts)) throw PageFetchException("허용 호스트 밖: $host")
        if (resolvesToPrivate(host)) throw PageFetchException("사설·내부 주소로 연결됨: $host")
        return uri
    }

    // https만 받던 경계(2026-10-24 사용자 "http도 수집가능하게"): https를 열지 않는 공식 사이트를 위해 http는 허용 호스트에 이름을 적은 호스트에서만
    fun schemeAllowed(scheme: String?, host: String, allowedHosts: List<String>): Boolean =
        scheme == "https" || (scheme == "http" && allowedHosts.any { it.equals(host, ignoreCase = true) })

    fun hostAllowed(host: String, allowedHosts: List<String>): Boolean =
        allowedHosts.isEmpty() || host.lowercase() in allowedHosts.map { it.lowercase() }

    // DNS가 사설·루프백·링크로컬·any 주소를 돌려주면 true. 이름을 못 풀면 연결도 안 되므로 true로 본다
    fun resolvesToPrivate(host: String): Boolean = try {
        InetAddress.getAllByName(host).any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
    } catch (_: java.net.UnknownHostException) {
        true
    }
}
