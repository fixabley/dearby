package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.profile.ProfileService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// native-v1.md "개인정보 동의": 현재 버전과 같은 동의를 기록한다(다르면 400 CONSENT_VERSION). 상태는 GET /v1/profile의 consent·consentRequired
@RestController
@RequestMapping("/v1/me/consents")
class ConsentController(private val profiles: ProfileService) {

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun give(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: ConsentRequest) =
        profiles.giveConsent(UUID.fromString(jwt.subject), request.privacyVersion.trim(), request.termsVersion.trim())
}

data class ConsentRequest(val privacyVersion: String = "", val termsVersion: String = "")
