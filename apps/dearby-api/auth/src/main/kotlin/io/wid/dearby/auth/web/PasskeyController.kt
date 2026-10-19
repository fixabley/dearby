package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.StoredPasskey
import io.wid.dearby.application.user.SignInMethodService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 설정 화면 "패스키"(native-v1.md "설정"): 목록·이름 바꾸기·삭제. 추가는 AuthController의 가입 흐름을 로그인 상태로 부른다. 인증 필요(api의 AccessRules)
@RestController
@RequestMapping("/v1/me/passkeys")
class PasskeyController(private val signInMethods: SignInMethodService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) =
        PasskeyListResponse(signInMethods.passkeys(UUID.fromString(jwt.subject)).map(::toResponse))

    // 남의 패스키·없는 id는 404
    @PatchMapping("/{id}")
    fun rename(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String, @RequestBody request: PasskeyLabelRequest) =
        toResponse(signInMethods.renamePasskey(UUID.fromString(jwt.subject), id, request.label))

    // 마지막 로그인 수단이면 409 LAST_SIGN_IN_METHOD. 남의 패스키·없는 id는 404
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) =
        signInMethods.deletePasskey(UUID.fromString(jwt.subject), id)

    private fun toResponse(passkey: StoredPasskey) =
        PasskeyResponse(passkey.credentialId, passkey.label, passkey.createdAt, passkey.lastUsedAt, passkey.backedUp, passkey.transports)
}
