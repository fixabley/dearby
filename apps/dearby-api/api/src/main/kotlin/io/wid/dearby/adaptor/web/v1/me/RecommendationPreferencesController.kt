package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.recommendation.RecommendationService
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.recommendation.RecommendationPreferences
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "추천 설정 퍼널·지원 자격 정보·추천": 퍼널 답. 저장한 적이 없으면 빈 배열과 completedAt null. PUT은 통째로 바꾸고 처음 저장한 시각을 완료로 남긴다
@RestController
@RequestMapping("/v1/me/recommendation-preferences")
class RecommendationPreferencesController(private val recommendations: RecommendationService) {

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt) = RecommendationPreferencesResponse.from(recommendations.preferences(UUID.fromString(jwt.subject)))

    @PutMapping
    fun put(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: RecommendationPreferencesRequest) = RecommendationPreferencesResponse.from(
        recommendations.savePreferences(
            UUID.fromString(jwt.subject),
            RecommendationPreferences(request.fieldTagIds, request.categories.map(ActivityCategory::parse), request.traitTagIds),
        )
    )
}

data class RecommendationPreferencesRequest(val fieldTagIds: List<Long> = emptyList(), val categories: List<String> = emptyList(), val traitTagIds: List<Long> = emptyList())

data class RecommendationPreferencesResponse(val fieldTagIds: List<Long>, val categories: List<String>, val traitTagIds: List<Long>, val completedAt: OffsetDateTime?) {
    companion object {
        fun from(preferences: RecommendationPreferences) =
            RecommendationPreferencesResponse(preferences.fieldTagIds, preferences.categories.map { it.name }, preferences.traitTagIds, preferences.completedAt)
    }
}
