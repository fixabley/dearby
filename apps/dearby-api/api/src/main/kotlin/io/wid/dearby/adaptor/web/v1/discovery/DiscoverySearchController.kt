package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.application.discovery.DiscoverySearchService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 필터 시트의 조직·프로그램 검색(native-v1.md "발견 필터 시트 표현" GET /v1/discovery/search). 인증 없이 받고(ApiAccessRules) 로그인이면 팔로우한 것이 먼저(Vary: Authorization)
@RestController
@RequestMapping("/v1/discovery/search")
class DiscoverySearchController(private val search: DiscoverySearchService) {

    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<DiscoverySearchResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val items = search.search(q, jwt?.let { UUID.fromString(it.subject) })
        return ResponseEntity.ok().varyBy(HttpHeaders.ACCEPT_LANGUAGE, HttpHeaders.AUTHORIZATION).header(HttpHeaders.CONTENT_LANGUAGE, language.tag)
            .body(DiscoverySearchResponse(items.map { DiscoverySearchItemResponse(it.type.name.lowercase(), it.id, language.pick(it.name, it.nameEn), it.logoUrl, it.category?.name, it.followed) }))
    }
}

data class DiscoverySearchResponse(val items: List<DiscoverySearchItemResponse>)

// type: organization|program(뒤에 activity를 더할 수 있다). category: 프로그램 기본 종류 코드(조직은 null). followed: 로그인 사용자가 팔로우·하트했는지
data class DiscoverySearchItemResponse(val type: String, val id: Long, val name: String, val logoUrl: String?, val category: String?, val followed: Boolean)
