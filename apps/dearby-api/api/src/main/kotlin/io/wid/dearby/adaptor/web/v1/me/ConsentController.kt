package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.domain.profile.AdConsent
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

// native-v1.md "개인정보 동의": 현재 버전과 같은 동의를 기록한다(다르면 400 CONSENT_VERSION). 상태는 GET /v1/profile의 consent·consentRequired.
// "광고 목적 개인정보 제3자 제공 동의(선택)": /ad-third-party에서 읽기·동의·철회. 앱은 맞춤 대기열에서 광고를 요청하기 직전에 읽는다
@RestController
@RequestMapping("/v1/me/consents")
class ConsentController(private val profiles: ProfileService) {

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun give(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: ConsentRequest) =
        profiles.giveConsent(UUID.fromString(jwt.subject), request.privacyVersion.trim(), request.termsVersion.trim())

    @GetMapping("/ad-third-party")
    fun adThirdParty(@AuthenticationPrincipal jwt: Jwt) = AdConsentStatusResponse.from(profiles.adConsent(UUID.fromString(jwt.subject)))

    @PutMapping("/ad-third-party")
    fun giveAdThirdParty(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: AdConsentRequest) =
        AdConsentStatusResponse.from(profiles.giveAdConsent(UUID.fromString(jwt.subject), request.version.trim()))

    @DeleteMapping("/ad-third-party")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdrawAdThirdParty(@AuthenticationPrincipal jwt: Jwt) = profiles.withdrawAdConsent(UUID.fromString(jwt.subject))
}

data class ConsentRequest(val privacyVersion: String = "", val termsVersion: String = "")

data class AdConsentRequest(val version: String = "")

data class AdConsentResponse(val version: String, val agreedAt: OffsetDateTime)

// consent는 기록이 없으면 null. agreed는 기록이 현재 버전일 때만 true(옛 버전이면 화면 문구가 바뀐 것이라 다시 묻는다); 앱은 agreed일 때만 광고 SDK를 시작한다
data class AdConsentStatusResponse(val consent: AdConsentResponse?, val agreed: Boolean) {
    companion object {
        fun from(c: AdConsent?) = AdConsentStatusResponse(c?.let { AdConsentResponse(it.version, it.consentedAt) }, c?.version == AdConsent.VERSION)
    }
}
