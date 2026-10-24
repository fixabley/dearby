package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.adaptor.web.v1.activities.ActivityResponse
import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.adaptor.web.v1.tags.TagIds
import io.wid.dearby.application.discovery.StoryScenes
import io.wid.dearby.application.recommendation.RecommendationService
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.recommendation.RecommendationPreferences
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

// native-v1.md "추천 설정 퍼널·지원 자격 정보·추천": 로그인 없이 받는다(ApiAccessRules). 로그인이면 저장된 설정(없으면 쿼리 값)과 지원 자격 정보,
// 비로그인이면 쿼리 fieldTagIds·categories·traitTagIds(쉼표 목록). 점수는 내보내지 않고 이유(reasons)만 싣는다
@RestController
@RequestMapping("/v1/discovery/recommendations")
class RecommendationController(private val recommendations: RecommendationService) {

    @GetMapping
    fun recommend(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestParam(required = false) fieldTagIds: String?,
        @RequestParam(required = false) categories: String?,
        @RequestParam(required = false) traitTagIds: String?,
        @RequestParam(defaultValue = RecommendationService.DEFAULT_SIZE.toString()) size: Int,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<RecommendationPageResponse> {
        val language = ResponseLanguage.from(acceptLanguage)
        val query = RecommendationPreferences(
            TagIds.parse(fieldTagIds).orEmpty().toList(), ActivityCategory.parseList(categories).orEmpty().toList(), TagIds.parse(traitTagIds).orEmpty().toList(),
        )
        val page = recommendations.recommend(jwt?.let { UUID.fromString(it.subject) }, query, size)
        val items = page.items.map {
            val activity = ActivityResponse.from(it.listing, language)
            RecommendationResponse(activity, ProgramSummaryResponse.from(it.program, language), it.reasons, StoryScenes.of(it.listing, it.reasons).map { s -> SceneResponse.from(s, activity.name) })
        }
        return ResponseEntity.ok().varyBy(HttpHeaders.ACCEPT_LANGUAGE, HttpHeaders.AUTHORIZATION).header(HttpHeaders.CONTENT_LANGUAGE, language.tag)
            .body(RecommendationPageResponse(items, page.total))
    }
}

// scenes: 비로그인 대기열 화면이 쓰는 장면(맞춤 대기열과 같은 모양)
data class RecommendationResponse(val activity: ActivityResponse, val program: ProgramSummaryResponse, val reasons: List<String>, val scenes: List<Map<String, Any?>>)

// total은 자르기 전 추천 후보 수(퍼널 결과 "지금 지원할 수 있는 활동 n개")
data class RecommendationPageResponse(val items: List<RecommendationResponse>, val total: Int)
