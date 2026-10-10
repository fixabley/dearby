package io.wid.dearby.adaptor.web

import io.wid.dearby.application.auth.AuthService
import io.wid.dearby.application.auth.PasskeyOptions
import io.wid.dearby.application.auth.PasskeyService
import io.wid.dearby.application.auth.TokenPair
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode
import java.util.*

data class RefreshRequest(val refreshToken: String)

data class RegistrationOptionsRequest(val displayName: String? = null)

// credential은 WebAuthn 표준 JSON(RegistrationResponseJSON·AuthenticationResponseJSON)
data class PasskeyRequest(val challengeId: UUID, val credential: JsonNode)

// 모두 인증 없이 받는다(SecurityConfig). 세션 쿠키를 쓰지 않고 challengeId로 옵션 요청과 완료 요청을 잇는다
@RestController
@RequestMapping("/v1/auth")
class AuthController(private val auth: AuthService, private val passkeys: PasskeyService) {

    @PostMapping("/passkeys/registration/options")
    fun registrationOptions(@RequestBody(required = false) request: RegistrationOptionsRequest?): PasskeyOptions =
        passkeys.registrationOptions(request?.displayName)

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
