package io.wid.dearby.auth.web

import io.wid.dearby.application.user.SignInMethodService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 설정 화면 "연결된 로그인"(native-v1.md "설정"): 연결된 애플·구글 계정 목록과 해제. 연결은 AppleLinkController·GoogleLinkController. 인증 필요(api의 AccessRules)
@RestController
@RequestMapping("/v1/me/identities")
class IdentityController(private val signInMethods: SignInMethodService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = IdentityListResponse(
        signInMethods.identities(UUID.fromString(jwt.subject)).map {
            IdentityResponse(it.provider.lowercase(), it.email, it.displayName, it.createdAt)
        }
    )

    // 마지막 로그인 수단이면 409 LAST_SIGN_IN_METHOD. 모르는 provider·연결 안 됨은 404
    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlink(@AuthenticationPrincipal jwt: Jwt, @PathVariable provider: String) =
        signInMethods.unlink(UUID.fromString(jwt.subject), provider)
}
