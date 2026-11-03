package io.wid.dearby.adaptor.web.v1.organizations

import io.wid.dearby.adaptor.web.ReferenceResponse
import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.adaptor.web.v1.discovery.ProgramSummaryResponse
import io.wid.dearby.application.favorite.FavoriteService
import io.wid.dearby.application.organization.OrganizationService
import io.wid.dearby.domain.favorite.FavoriteIds
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 조직 화면(native-v1.md "조직 화면·조직 팔로우"). 인증 없이 받고(ApiAccessRules) 로그인이면 followed·favorited가 켜진다(Vary: Authorization)
@RestController
@RequestMapping("/v1/organizations/{id}")
class OrganizationController(private val organizations: OrganizationService, private val favorites: FavoriteService) {

    @GetMapping
    fun get(
        @PathVariable id: Long,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<OrganizationResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val userId = jwt?.let { UUID.fromString(it.subject) }
        val detail = organizations.detail(id, userId)
        val mine = userId?.let { favorites.ids(it) } ?: FavoriteIds.NONE
        return ResponseEntity.ok().varyBy(HttpHeaders.ACCEPT_LANGUAGE, HttpHeaders.AUTHORIZATION).header(HttpHeaders.CONTENT_LANGUAGE, language.tag)
            .body(OrganizationResponse.from(detail, language, mine))
    }

    // 조직 경로: 최상위부터 바로 위 조직까지(응답 `ancestors`와 같다). 최상위 조직은 빈 목록, 없는 조직은 404
    @GetMapping("/ancestors")
    fun ancestors(@PathVariable id: Long) = OrganizationAncestorsResponse(organizations.ancestors(id).map { (ancestorId, name) -> ReferenceResponse(ancestorId, name) })
}

data class OrganizationResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val logoUrl: String?, // 자기 로고, 없으면 상위 조직 로고(native-v1.md "조직 로고와 로고 계승"). 없으면 null
    val brandColor: String?, // 그 로고의 대표색 "#RRGGBB", 없으면 null
    val ancestors: List<ReferenceResponse>, // 최상위부터 바로 위 조직까지. 최상위 조직은 []
    val children: List<OrganizationChildResponse>, // 하위 조직(이름 순 아님, id 순). 없으면 []
    val programs: List<ProgramSummaryResponse>, // 이 조직의 프로그램 묶음(모집 중 먼저, 그 뒤 최근 활동 순). 게시 활동이 없는 프로그램은 빠진다
    val counts: OrganizationCountsResponse,
    val followed: Boolean, // 로그인 사용자가 팔로우했는지. 비로그인은 false
) {
    companion object {
        fun from(detail: OrganizationService.Detail, language: ResponseLanguage, favorites: FavoriteIds) = OrganizationResponse(
            id = detail.organization.id,
            name = language.pick(detail.organization.name, detail.organization.nameEn),
            nameEn = detail.organization.nameEn,
            logoUrl = detail.logoUrl,
            brandColor = detail.brandColor,
            ancestors = detail.ancestors.map { (id, name) -> ReferenceResponse(id, name) },
            children = detail.children.map { OrganizationChildResponse(it.organization.id, language.pick(it.organization.name, it.organization.nameEn), it.logoUrl) },
            programs = detail.programs.map { ProgramSummaryResponse.from(it, language, favorites) },
            counts = OrganizationCountsResponse(detail.counts.programs, detail.counts.recruiting, detail.counts.past),
            followed = detail.followed,
        )
    }
}

data class OrganizationChildResponse(val id: Long, val name: String, val logoUrl: String?)

// programs: 프로그램 수, recruiting: 모집 중 활동 수, past: 종료된 활동 수
data class OrganizationCountsResponse(val programs: Int, val recruiting: Int, val past: Int)

data class OrganizationAncestorsResponse(val items: List<ReferenceResponse>)
