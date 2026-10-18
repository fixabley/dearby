package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.GoogleSignInService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 로그인한 사용자(패스키 계정)에 구글 계정을 연결한다. 인증 필요(앱의 AccessRules)
@RestController
@RequestMapping("/v1/me/identities")
class GoogleLinkController(private val google: GoogleSignInService) {

    @PostMapping("/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun link(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: AppleLinkTokenRequest) =
        google.link(UUID.fromString(jwt.subject), request.linkToken)
}
