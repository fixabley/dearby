package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.recommendation.RecommendationService
import io.wid.dearby.domain.recommendation.EligibilityProfile
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "추천 설정 퍼널·지원 자격 정보·추천": 지원 자격 정보. 본인만 읽고, 현재 동의 버전(consentVersion)과 함께만 저장한다(다르면 400 CONSENT_VERSION).
// 값은 명함·공개 프로필·운영자 화면·로그 어디에도 남기지 않는다. DELETE는 값과 동의 기록을 함께 지운다
@RestController
@RequestMapping("/v1/me/eligibility-profile")
class EligibilityProfileController(private val recommendations: RecommendationService) {

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt) = EligibilityProfileResponse.from(recommendations.eligibility(UUID.fromString(jwt.subject)))

    @PutMapping
    fun put(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: EligibilityProfileRequest) = EligibilityProfileResponse.from(
        recommendations.saveEligibility(UUID.fromString(jwt.subject), request.consentVersion.trim(), request.educationStatus, request.employment, request.schoolYear, request.birthYear)
    )

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt) = recommendations.deleteEligibility(UUID.fromString(jwt.subject))
}

data class EligibilityProfileRequest(
    val consentVersion: String = "",
    val educationStatus: String? = null,
    val employment: String? = null,
    val schoolYear: Int? = null,
    val birthYear: Int? = null,
)

data class EligibilityConsentResponse(val version: String, val agreedAt: OffsetDateTime)

// 저장한 적이 없으면 모두 null
data class EligibilityProfileResponse(
    val consent: EligibilityConsentResponse?,
    val educationStatus: String?,
    val employment: String?,
    val schoolYear: Int?,
    val birthYear: Int?,
) {
    companion object {
        fun from(profile: EligibilityProfile?) = EligibilityProfileResponse(
            profile?.let { EligibilityConsentResponse(it.consentVersion, it.consentedAt) },
            profile?.educationStatus, profile?.employment, profile?.schoolYear, profile?.birthYear,
        )
    }
}
