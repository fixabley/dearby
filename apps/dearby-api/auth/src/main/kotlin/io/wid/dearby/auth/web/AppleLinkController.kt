package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.AppleSignInService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 로그인한 사용자(패스키 계정)에 애플 계정을 연결한다. 인증 필요(api의 AccessRules). 인증 경로(/v1/auth)는 bearer를 읽지 않아 여기 둔다
@RestController
@RequestMapping("/v1/me/identities")
class AppleLinkController(private val apple: AppleSignInService) {

    @PostMapping("/apple")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun link(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: AppleLinkTokenRequest) =
        apple.link(UUID.fromString(jwt.subject), request.linkToken)
}
