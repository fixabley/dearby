package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.application.discovery.DiscoveryFilterService
import io.wid.dearby.domain.DiscoveryKind
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

// 발견 필터 시트 구성(native-v1.md "발견 필터 시트 표현" GET /v1/discovery/filters): 어드민이 정한 섹션·항목에 현재 kind·검색어·칩 기준 프로그램 수.
// 인증 없이 받고(ApiAccessRules) 사용자별 값이 없어 공개 캐시 5분
@RestController
@RequestMapping("/v1/discovery/filters")
class DiscoveryFilterController(private val filters: DiscoveryFilterService) {

    @GetMapping
    fun get(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) kind: String?, // program|event(native-v1.md "발견 프로그램·이벤트 전환")
        @RequestParam(required = false) organizationIds: String?, // 고른 조직·프로그램 칩(하위 조직 포함). 개수에만 반영
        @RequestParam(required = false) programIds: String?,
    ): ResponseEntity<DiscoveryFiltersResponse> {
        val sections = filters.sections(q, DiscoveryKind.parse(kind), DiscoveryProgramController.scope(organizationIds, programIds))
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(DiscoveryFiltersResponse(sections))
    }
}

// sections[].key: status|category|field|eligibility. status의 code는 세 묶음 id(available·in_progress·past), category는 종류 코드, field는 분야 태그 id, eligibility는 mine 하나.
// items[].icon: "아이콘 통일" 표의 키(앱이 모르는 키는 기본 아이콘), count: 그 조건의 프로그램 수(eligibility는 사용자별이라 null)
data class DiscoveryFiltersResponse(val sections: List<DiscoveryFilterService.Section>)
