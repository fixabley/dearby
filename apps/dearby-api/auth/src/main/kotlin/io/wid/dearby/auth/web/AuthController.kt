package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.AuthService
import io.wid.dearby.application.auth.PasskeyOptions
import io.wid.dearby.application.auth.PasskeyService
import io.wid.dearby.application.auth.TokenPair
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 모두 인증 없이 받는다(SecurityConfig). 세션 쿠키를 쓰지 않고 challengeId로 옵션 요청과 완료 요청을 잇는다
@RestController
@RequestMapping("/v1/auth")
class AuthController(private val auth: AuthService, private val passkeys: PasskeyService) {

    // 가입 options에만 Bearer를 읽는다(SecurityConfig). 로그인한 사용자가 부르면 그 계정에 패스키를 더한다(native-v1.md "설정")
    @PostMapping("/passkeys/registration/options")
    fun registrationOptions(@AuthenticationPrincipal jwt: Jwt?, @RequestBody(required = false) request: RegistrationOptionsRequest?): PasskeyOptions =
        passkeys.registrationOptions(request?.displayName, jwt?.let { UUID.fromString(it.subject) })

    @PostMapping("/passkeys/registration")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: PasskeyRequest): TokenPair =
        passkeys.register(request.challengeId, request.credential)

    @PostMapping("/passkeys/authentication/options")
    fun authenticationOptions(): PasskeyOptions = passkeys.authenticationOptions()

    @PostMapping("/passkeys/authentication")
    fun authenticate(@RequestBody request: PasskeyRequest): TokenPair =
        passkeys.authenticate(request.challengeId, request.credential)

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): TokenPair = auth.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestBody request: RefreshRequest) = auth.logout(request.refreshToken)
}
